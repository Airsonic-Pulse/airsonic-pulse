/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import org.airsonic.player.command.CredentialsManagementCommand;
import org.airsonic.player.command.CredentialsManagementCommand.CredentialsCommand;
import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.repository.UserCredentialRepository;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.security.PasswordDecoder;
import org.airsonic.player.security.PasswordEncoderConfig;
import org.airsonic.player.service.cache.UserCache;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides security-related services for authentication and authorization.
 *
 * @author Sindre Mehus
 */
@Service
public class SecurityService implements UserDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityService.class);

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserCredentialRepository userCredentialRepository;
    @Autowired
    private UserCache userCache;

    /**
     * Locates the user based on the username.
     *
     * @param username The username
     * @return A fully populated user record (never <code>null</code>)
     * @throws UsernameNotFoundException if the user could not be found or the user
     *                                   has no GrantedAuthority.
     * @throws DataAccessException       If user could not be found for a
     *                                   repository-specific reason.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        return loadUserByUsername(username, true);
    }

    public UserDetails loadUserByUsername(String username, boolean caseSensitive)
            throws UsernameNotFoundException, DataAccessException {
        User user = getUserByName(username, caseSensitive);
        if (user == null) {
            throw new UsernameNotFoundException("User \"" + username + "\" was not found.");
        }

        List<GrantedAuthority> authorities = getGrantedAuthorities(user);

        return new UserDetail(
                username,
                getCredentials(user.getUsername(), App.AIRSONIC),
                true,
                true,
                true,
                true,
                authorities);
    }

    /**
     * Creates a credential for a user
     *
     * @param username The username to create the credential for
     * @param command  The command containing the credential
     * @param comment  The comment to add to the credential
     * @return true if the credential was created successfully
     */
    @Transactional
    public boolean createCredential(String username, CredentialsCommand command, String comment) {

        Optional<User> user = userRepository.findByUsername(username);
        return user.map(u -> {
            try {
                UserCredential userCredential = new UserCredential(
                        u,
                        command.getApp().getUsernameRequired() ? command.getUsername() : username,
                        PasswordEncoderConfig.ENCODERS.get(command.getEncoder()).encode(command.getCredential()),
                        command.getEncoder(),
                        command.getApp(),
                        comment,
                        command.getExpirationInstant());
                userCredentialRepository.save(userCredential);
                return true;
            } catch (Exception e) {
                LOG.warn("Can't create a credential user {}", username, e);
                return false;
            }
        }).orElseGet(() -> {
            LOG.warn("Can't create a credential for a non-existent user {}", username);
            return false;
        });
    }

    /**
     * Updates credentials for a user
     *
     * @param username                  The username to update the credentials for
     * @param command                   The command containing the credentials
     * @param comment                   The comment to add to the credentials
     * @param reencodePlaintextNewCreds if true, reencode the new credential using
     *                                  the new encoder
     * @return true if the credentials were updated successfully
     */
    @Transactional
    public boolean updateCredentials(String username, CredentialsManagementCommand command, String comment,
            boolean reencodePlaintextNewCreds) {

        Optional<User> user = userRepository.findByUsername(username);

        if (user.isEmpty()) {
            LOG.warn("Can't update credentials for a non-existent user {}", username);
            return false;
        }

        List<Boolean> failures = new ArrayList<>();
        user.ifPresent(u -> {
            List<UserCredential> userCredentials = userCredentialRepository.findByUserAndAppIn(u,
                    List.of(App.values()));

            command.getCredentials().forEach(c -> {
                userCredentials.stream().filter(sc -> Strings.CS.equals(String.valueOf(sc.hashCode()), c.getHash()))
                        .findFirst().ifPresent(dbCreds -> {
                            if (c.getMarkedForDeletion()) {
                                if (!deleteCredential(dbCreds)) {
                                    LOG.warn("Can't delete credential for user {}", username);
                                    failures.add(true);
                                }
                            } else {
                                if (dbCreds.updateEncoder(c.getEncoder(),
                                        reencodePlaintextNewCreds)) {
                                    dbCreds.setComment(comment);
                                    dbCreds.setUpdated(Instant.now());
                                    dbCreds.setExpiration(c.getExpirationInstant());
                                    userCredentialRepository.save(dbCreds);
                                } else {
                                    LOG.warn("Can't update credential for user {}", username);
                                    failures.add(true);
                                }
                            }
                        });
            });
        });

        return failures.isEmpty();
    }

    /**
     * Recovers a credential for a user. Sets the user to non-ldap authenticated and
     * creates a new airsonic credential
     *
     * @param username The username to recover the credential for
     * @param password The password to create the credential for
     * @param comment  The comment to add to the credential
     */
    @Transactional
    public void recoverCredential(String username, String password, String comment) {
        if (StringUtils.isBlank(username)) {
            LOG.warn("Can't recover credential for a blank username");
            return;
        }
        userCache.removeUser(username);
        userRepository.findByUsername(username).ifPresentOrElse(u -> {
            u.setLdapAuthenticated(false);
            userRepository.save(u);
            List<UserCredential> existing = userCredentialRepository.findByUserUsernameAndApp(username, App.AIRSONIC);
            userCredentialRepository.deleteAll(existing);
            createAirsonicCredentialToUser(u, password, comment);
        }, () -> {
                LOG.warn("Can't recover credential for a non-existent user {}", username);
            }
        );
    }

    /**
     * Creates a credential for a user with the default encoder for the app
     *
     * @param username The username to create the credential for
     * @param password The password to create the credential for
     * @param comment  The comment to add to the credential
     */
    public boolean createAirsonicCredential(String username, String password, String comment) {
        User user = getUserByName(username);
        if (user == null) {
            LOG.warn("Can't create a credential for a non-existent user {}", username);
            return false;
        } else {
            return createAirsonicCredentialToUser(user, password, comment);
        }
    }

    /**
     * * Creates a credential for a user with the default encoder for the app *
     * * @param user The user to create the credential for * @param password The
     * password to create the credential for * @param comment The comment to add to
     * the credential * @return true if the credential was created successfully
     */
    @Transactional
    private boolean createAirsonicCredentialToUser(User user, String password, String comment) {
        String encoder = getPreferredPasswordEncoder(true);
        try {
            UserCredential userCredential = new UserCredential(user, user.getUsername(),
                    PasswordEncoderConfig.ENCODERS.get(encoder).encode(password), encoder, App.AIRSONIC, comment);
            userCredentialRepository.save(userCredential);
        } catch (Exception e) {
            LOG.warn("Can't create a credential user {}", user.getUsername(), e);
            return false;
        }
        return true;
    }

    /**
     * * Deletes a credential. Ensures we can't delete the last airsonic credential
     * * * @param creds The credential to delete * @return true if the credential
     * was deleted successfully
     */
    @Transactional
    public boolean deleteCredential(UserCredential creds) {
        if (creds == null || creds.getUser() == null) {
            LOG.warn("Can't delete a null credential");
            return false;
        }
        if (App.AIRSONIC == creds.getApp()) {
            if (userCredentialRepository.countByUserAndApp(creds.getUser(), App.AIRSONIC) == 1) {
                LOG.warn("Can't delete the last airsonic credential for user {}", creds.getUser().getUsername());
                return false;
            }
        }
        try {
            userCredentialRepository.delete(creds);
        } catch (Exception e) {
            LOG.info("Can't delete a credential", e);
            return false;
        }
        return true;
    }

    public List<UserCredential> getCredentials(String username, App... apps) {
        return userRepository.findByUsername(username).map(user -> {
            return userCredentialRepository.findByUserAndAppIn(user, List.of(apps));
        }).orElseGet(() -> {
            return Collections.emptyList();
        });
    }

    public Map<App, UserCredential> getDecodableCredsForApps(String username, App... apps) {
        return getCredentials(username, apps).parallelStream()
                .filter(c -> PasswordEncoderConfig.DECODABLE_ENCODERS.contains(c.getEncoder()))
                .filter(c -> c.getExpiration() == null || c.getExpiration().isAfter(Instant.now()))
                .collect(Collectors.groupingByConcurrent(
                        UserCredential::getApp,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(c -> c.getUpdated())), o -> o.orElse(null))));
    }

    public boolean checkDefaultAdminCredsPresent() {
        return userCredentialRepository.findByUserUsernameAndApp(User.USERNAME_ADMIN, App.AIRSONIC).parallelStream()
                .anyMatch(c -> PasswordEncoderConfig.ENCODERS.get(c.getEncoder()).matches(User.USERNAME_ADMIN,
                        c.getCredential()));
    }

    public boolean checkOpenCredsPresent() {
        return userCredentialRepository.existsByEncoderIn(PasswordEncoderConfig.OPENTEXT_ENCODERS);
    }

    public boolean checkLegacyCredsPresent() {
        return userCredentialRepository.existsByEncoderStartsWith("legacy");
    }

    /**
     * Migrates legacy credentials to non-legacy credentials
     *
     * @param useDecodableOnly if true, only migrate to decodable encoders
     * @return true if all credentials were migrated successfully
     */
    @Transactional
    public boolean migrateLegacyCredsToNonLegacy(boolean useDecodableOnly) {
        String decodableEncoder = settingsService.getDecodablePasswordEncoder();
        String nonDecodableEncoder = useDecodableOnly ? decodableEncoder
                : settingsService.getNonDecodablePasswordEncoder();

        List<UserCredential> failures = new ArrayList<>();

        userCredentialRepository.findByEncoderStartsWith("legacy").forEach(c -> {
            c.setComment(c.getComment() + " | Migrated to nonlegacy by admin");
            String encoder = App.AIRSONIC == c.getApp() ? nonDecodableEncoder : decodableEncoder;
            if (c.updateEncoder(encoder, false)) {
                userCredentialRepository.save(c);
            } else {
                LOG.warn("System failed to migrate creds created on {} for user {}", c.getCreated(),
                        c.getUser().getUsername());
                failures.add(c);
            }
        });

        return failures.isEmpty();
    }

    public String getPreferredPasswordEncoder(boolean nonDecodableAllowed) {
        if (!nonDecodableAllowed || !settingsService.getPreferNonDecodablePasswords()) {
            return settingsService.getDecodablePasswordEncoder();
        } else {
            return settingsService.getNonDecodablePasswordEncoder();
        }
    }

    public static List<GrantedAuthority> getGrantedAuthorities(User user) {
        return Stream.concat(
                Stream.of(
                        new SimpleGrantedAuthority("IS_AUTHENTICATED_ANONYMOUSLY"),
                        new SimpleGrantedAuthority("ROLE_USER")),
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())))
                .collect(Collectors.toList());
    }

    public static final String decodeCredentials(UserCredential reversibleCred) {
        PasswordDecoder decoder = (PasswordDecoder) PasswordEncoderConfig.ENCODERS.get(reversibleCred.getEncoder());
        try {
            return decoder.decode(reversibleCred.getCredential());
        } catch (Exception e) {
            LOG.warn("Could not decode credentials for user {}, app {}", reversibleCred.getUser().getUsername(),
                    reversibleCred.getApp(), e);
            return null;
        }
    }

    /**
     * Returns the currently logged-in user for the given HTTP request.
     *
     * @param request The HTTP request.
     * @return The logged-in user, or <code>null</code>.
     */
    public User getCurrentUser(HttpServletRequest request) {
        String username = getCurrentUsername(request);
        return username == null ? null : getUserByName(username);
    }

    /**
     * Returns the name of the currently logged-in user.
     *
     * @param request The HTTP request.
     * @return The name of the logged-in user, or <code>null</code>.
     */
    public String getCurrentUsername(HttpServletRequest request) {
        return new SecurityContextHolderAwareRequestWrapper(request, null).getRemoteUser();
    }

    /**
     * Returns the user with the given username.
     *
     * @param username The username used when logging in.
     * @return The user, or <code>null</code> if not found.
     */
    @Nullable
    public User getUserByName(@Nullable String username) {
        return getUserByName(username, true);
    }

    /**
     * Returns the user with the given username<br>
     * Cache note: Will only cache if case-sensitive. Otherwise, cache eviction is
     * difficult
     *
     * @param username      The username to look for
     * @param caseSensitive If false, will do a case insensitive search
     * @return The corresponding User
     */
    public User getUserByName(@Param("username") String username, boolean caseSensitive) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        if (caseSensitive) {
            User user = userCache.getUser(username);
            if (user == null) {
                user = userRepository.findByUsername(username).orElse(null);
                userCache.putUser(username, user);
            }
            return user;
        } else {
            return userRepository.findByUsernameIgnoreCase(username).orElse(null);
        }
    }

    /**
     * Increment the number of bytes downloaded by the given user.
     *
     * @param username             user name.
     * @param deltaBytesDownloaded number of bytes downloaded.
     * @return updated user.
     */
    // TODO: This is not security related. Move to a different service.
    @Transactional
    public User incrementBytesStreamed(String username, long deltaBytesStreamed) {
        User user = getUserByName(username);
        if (Objects.nonNull(user)) {
            user.setBytesStreamed(user.getBytesStreamed() + deltaBytesStreamed);
            userRepository.save(user);
        }
        return user;
    }

    /**
     * Increment the number of bytes streamed by the given user.
     *
     * @param username           user name.
     * @param deltaBytesStreamed number of bytes streamed.
     * @return updated user.
     */
    // TODO: This is not security related. Move to a different service.
    @Transactional
    public User incrementBytesDownloaded(String username, long deltaBytesDownloaded) {
        User user = getUserByName(username);
        if (Objects.nonNull(user)) {
            user.setBytesDownloaded(user.getBytesDownloaded() + deltaBytesDownloaded);
            userRepository.save(user);
        }
        return user;
    }

    /**
     * Increment the number of bytes uploaded by the given user.
     *
     * @param username           user name.
     * @param deltaBytesUploaded number of bytes uploaded.
     * @return updated user.
     */
    // TODO: This is not security related. Move to a different service.
    @Transactional
    public User incrementBytesUploaded(String username, long deltaBytesUploaded) {
        User user = getUserByName(username);
        if (Objects.nonNull(user)) {
            user.setBytesUploaded(user.getBytesUploaded() + deltaBytesUploaded);
            userRepository.save(user);
        }
        return user;
    }

    /**
     * delete user.
     *
     * @param username        user name.
     * @param currentUsername current user name.
     */
    @Transactional
    public void deleteUser(String username, String currentUsername) {
        if (StringUtils.isNotBlank(username) && username.equals(currentUsername)) {
            throw new SelfDeletionException();
        }
        userCache.removeUser(username);
        userRepository.deleteById(username);
        LOG.info("User {} deleted by {}", username, currentUsername);
    }

    /**
     * Returns the user with the given email address.
     *
     * @param email The email address.
     * @return The user, or <code>null</code> if not found.
     */
    public User getUserByEmail(String email) {
        if (StringUtils.isBlank(email)) {
            LOG.warn("Email is blank");
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }

    /**
     * Returns whether the given user has administrative rights.
     */
    public boolean isAdmin(String username) {
        if (User.USERNAME_ADMIN.equals(username)) {
            return true;
        }
        User user = getUserByName(username);
        return user != null && user.isAdminRole();
    }

    /**
     * Creates a new user.
     *
     * @param user       The user to create.
     * @param credential The raw credential (will be encoded)
     */
    @Transactional
    public void createUser(User user, String credential, String comment) {
        String defaultEncoder = getPreferredPasswordEncoder(true);
        UserCredential uc = new UserCredential(
                user,
                user.getUsername(),
                PasswordEncoderConfig.ENCODERS.get(defaultEncoder).encode(credential),
                defaultEncoder,
                App.AIRSONIC,
                comment);
        userRepository.saveAndFlush(user);
        userCredentialRepository.saveAndFlush(uc);
        mediaFolderService.setMusicFoldersForUser(user.getUsername(),
                MusicFolder.toIdList(mediaFolderService.getAllMusicFolders()));
        LOG.info("Created user {}", user.getUsername());
    }

    /**
     * create guest user if not exists.
     */
    public void createGuestUserIfNotExists() {
        if (!userRepository.existsById(User.USERNAME_GUEST)) {
            User user = new User(User.USERNAME_GUEST, null);
            user.setRoles(Set.of(Role.STREAM));
            RandomStringGenerator generator = new RandomStringGenerator.Builder().withinRange('0', 'z')
                .filteredBy(c -> Character.isLetterOrDigit(c))
                .get();
            createUser(user, generator.generate(30),
                    "Autogenerated for " + User.USERNAME_GUEST + " user");
        }
    }

    /**
     *
     */
    @Transactional
    public User updateUserByUserSettingsCommand(@Param("command") UserSettingsCommand command) {
        // check
        if (Objects.isNull(command)) {
            LOG.warn("Can't update a null command");
            return null;
        }

        // get user
        User user = getUserByName(command.getUsername());

        // update
        if (Objects.nonNull(user)) {
            user.setEmail(StringUtils.trimToNull(command.getEmail()));
            user.setLdapAuthenticated(command.isLdapAuthenticated());
            user.setPasswordAuthEnabled(command.isPasswordAuthEnabled());
            Set<Role> roles = new HashSet<>();
            if (command.isAdminRole())
                roles.add(Role.ADMIN);
            if (command.isDownloadRole())
                roles.add(Role.DOWNLOAD);
            if (command.isUploadRole())
                roles.add(Role.UPLOAD);
            if (command.isCoverArtRole())
                roles.add(Role.COVERART);
            if (command.isCommentRole())
                roles.add(Role.COMMENT);
            if (command.isPodcastRole())
                roles.add(Role.PODCAST);
            if (command.isStreamRole())
                roles.add(Role.STREAM);
            if (command.isJukeboxRole())
                roles.add(Role.JUKEBOX);
            if (command.isSettingsRole())
                roles.add(Role.SETTINGS);
            if (command.isShareRole())
                roles.add(Role.SHARE);
            user.setRoles(roles);
            userRepository.save(user);
            if (command.isPasswordChange())
                createAirsonicCredentialToUser(user, command.getPassword(), "Created by admin");
        }
        userCache.removeUser(command.getUsername());
        return user;
    }

    /**
     * Self-service write path for the per-user {@code password_auth_enabled} flag (#278). Updates the
     * column and evicts the {@link UserCache} so the legacy-auth gate — which resolves the user via
     * the cache-backed {@link #getUserByName} — sees the new value immediately rather than after the
     * cache TTL. The caller is responsible for supplying the authenticated principal's own username
     * (the credentials page derives it from the security context, never from a form field).
     *
     * @param username the user whose flag is updated
     * @param enabled  whether legacy password/token authentication is allowed for that user
     */
    public void updatePasswordAuthEnabled(String username, boolean enabled) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setPasswordAuthEnabled(enabled);
            userRepository.save(user);
        });
        userCache.removeUser(username);
    }

    public boolean isReadAllowed(MediaFile file, boolean checkExistence) {
        if (file == null) {
            return false;
        }
        MusicFolder folder = file.getFolder();
        return folder.isEnabled() && (!checkExistence || Files.exists(file.getFullPath()));
    }

    public boolean isWriteAllowed(Path relativePath, MusicFolder folder) {
        // Only allowed to write podcasts or cover art.
        return folder.isEnabled()
                && (folder.getType() == Type.PODCAST || relativePath.getFileName().toString().startsWith("cover."));
    }

    /**
     * Returns whether the given file may be uploaded.
     *
     * @return Whether the given file may be uploaded.
     */
    public void checkUploadAllowed(Path file, boolean checkFileExists) throws IOException {
        if (mediaFolderService.getMusicFolderForFile(file) == null) {
            throw new AccessDeniedException(file.toString(), null,
                    "Specified location is not in writable music folder");
        }

        if (checkFileExists && Files.exists(file)) {
            throw new FileAlreadyExistsException(file.toString(), null, "File already exists");
        }
    }

    public boolean isFolderAccessAllowed(MediaFile file, String username) {
        return mediaFolderService.getMusicFoldersForUser(username).parallelStream()
                .anyMatch(musicFolder -> musicFolder.getId().equals(file.getFolder().getId()));
    }

    public static class UserDetail extends org.springframework.security.core.userdetails.User {
        private List<UserCredential> creds;

        public UserDetail(String username, List<UserCredential> creds, boolean enabled, boolean accountNonExpired,
                boolean credentialsNonExpired, boolean accountNonLocked,
                Collection<? extends GrantedAuthority> authorities) {
            super(username,
                    DigestUtils.md5Hex(
                            creds.stream().map(x -> x.getEncoder() + "/" + x.getCredential() + "/" + x.getExpiration())
                                    .collect(Collectors.joining())),
                    enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);

            this.creds = creds;
        }

        public List<UserCredential> getCredentials() {
            return creds;
        }

        @Override
        public void eraseCredentials() {
            super.eraseCredentials();
            creds = null;
        }
    }

    public static class SelfDeletionException extends RuntimeException {

        public SelfDeletionException() {
            super("Can't delete yourself");
        }
    }

}