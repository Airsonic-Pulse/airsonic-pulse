package org.airsonic.player.service;

import com.google.common.collect.Streams;
import org.airsonic.player.command.MusicFolderSettingsCommand.MusicFolderInfo;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@Service
public class MediaFolderService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaFolderService.class);

    @Autowired
    private MusicFolderRepository musicFolderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;
    @Autowired
    private CoverArtRepository coverArtRepository;

    private List<MusicFolder> cachedMusicFolders;
    private final ConcurrentMap<String, List<MusicFolder>> cachedMusicFoldersPerUser = new ConcurrentHashMap<>();

    /**
     * Returns all music folders. Non-existing and disabled folders are not included.
     *
     * @return Possibly empty list of all music folders.
     */
    public List<MusicFolder> getAllMusicFolders() {
        return getAllMusicFolders(false, false);
    }

    /**
     * Returns all music folders.
     *
     * @param includeDisabled    Whether to include disabled folders.
     * @param includeNonExisting Whether to include non-existing folders.
     * @return Possibly empty list of all music folders.
     */
    public List<MusicFolder> getAllMusicFolders(boolean includeDisabled, boolean includeNonExisting) {
        if (cachedMusicFolders == null) {
            cachedMusicFolders = musicFolderRepository.findByDeleted(false);
        }
        return cachedMusicFolders.stream()
                .filter(folder -> (includeDisabled || folder.isEnabled()) && (includeNonExisting || Files.exists(folder.getPath())))
                .toList();
    }

    public List<MusicFolder> getAllMusicFolders(boolean includeDisabled, boolean includeNonExisting, boolean includeDeleted) {
        return Streams.concat(
                getAllMusicFolders(includeDisabled, includeNonExisting).stream(),
                includeDeleted ? getDeletedMusicFolders().stream() : Stream.empty())
            .toList();
    }

    /**
     * Returns all music folders a user have access to. Non-existing and disabled folders are not included.
     * @param username Username to get music folders for.
     * @return Possibly empty list of music folders.
     */
    @Transactional
    public List<MusicFolder> getMusicFoldersForUser(String username) {
        return cachedMusicFoldersPerUser.computeIfAbsent(username, u -> {
            return userRepository.findByUsername(u)
                .map(user -> {
                    return user.getMusicFolders()
                        .stream()
                        .filter(folder -> folder.isEnabled() && !folder.isDeleted() && Files.exists(folder.getPath()))
                        .toList();
                })
                .orElse(new ArrayList<>());
        });
    }

    /**
     * Returns all music folders a user have access to. Non-existing and disabled folders are not included.
     *
     * @param selectedMusicFolderId If non-null and positive and included in the list of allowed music folders, this methods returns a list of only this music folder.
     * @return Possibly empty list of music folders.
     */
    public List<MusicFolder> getMusicFoldersForUser(String username, Integer selectedMusicFolderId) {
        return getMusicFoldersForUser(username).stream()
                .filter(f -> selectedMusicFolderId == null || selectedMusicFolderId < 0 || f.getId().equals(selectedMusicFolderId))
                .toList();
    }

    @Transactional
    public void setMusicFoldersForUser(String username, Collection<Integer> musicFolderIds) {
        List<MusicFolder> folders = musicFolderRepository.findAllById(musicFolderIds);
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setMusicFolders(folders);
            userRepository.save(u);
        });
        cachedMusicFoldersPerUser.remove(username);
    }

    public MusicFolder getMusicFolderById(Integer id) {
        return getMusicFolderById(id, false, false);
    }

    public MusicFolder getMusicFolderById(Integer id, boolean includeDisabled, boolean includeNonExisting) {
        return getAllMusicFolders(includeDisabled, includeNonExisting).stream().filter(folder -> id.equals(folder.getId())).findAny().orElse(null);
    }

    @Transactional
    public void createMusicFolder(MusicFolder musicFolder) {
        List<MusicFolder> registeredMusicFolders = musicFolderRepository.findAll();
        Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps = getMusicFolderPathOverlaps(musicFolder, registeredMusicFolders);

        // deny same path music folders
        if (!overlaps.getLeft().isEmpty()) {
            throw new IllegalArgumentException("Music folder with path " + musicFolder.getPath() + " overlaps with existing music folder path(s) (" + logMusicFolderOverlap(overlaps) + ") and can therefore not be created.");
        }

        musicFolderRepository.saveAndFlush(musicFolder);
        userRepository.findAll().forEach(u -> {
            u.addMusicFolder(musicFolder);
            userRepository.save(u);
        });

        // if new folder has ancestors, reassign portion of closest ancestor's tree to new folder
        if (!overlaps.getMiddle().isEmpty()) {
            MusicFolder ancestor = overlaps.getMiddle().getFirst();
            reassignChildren(ancestor, musicFolder);
            clearMediaFileCache();
        }

        if (!overlaps.getRight().isEmpty()) {
            // if new folder has deleted descendants, integrate and true delete the descendants
            overlaps.getRight().stream()
                // deleted
                .filter(f -> f.isDeleted())
                .forEach(f -> {
                    reassignChildren(f, musicFolder);
                    musicFolderRepository.delete(f);
                    clearMediaFileCache();
                });
            // other descendants are ignored, they'll stay under descendant hierarchy
        }

        clearMusicFolderCache();
    }

    private void reassignChildren(MusicFolder oldFolder, MusicFolder newFolder) {
        if (oldFolder == null || newFolder == null) {
            return;
        }

        if (newFolder.getPath().getNameCount() > oldFolder.getPath().getNameCount()) {
            MusicFolder ancestor = oldFolder;
            MusicFolder descendant = newFolder;
            Path relativePath = ancestor.getPath().relativize(descendant.getPath());
            mediaFileRepository.findByFolderAndPathStartsWith(oldFolder, relativePath.toString() + File.separator).forEach(f -> {
                f.setFolder(newFolder);
                f.setPath(relativePath.relativize(f.getRelativePath()).toString());
                f.setParentPath(relativePath.relativize(f.getRelativeParentPath()).toString());
                mediaFileRepository.save(f);
            });

            coverArtRepository.findByFolderAndPathStartsWith(oldFolder, relativePath.toString() + File.separator).forEach(ca -> {
                ca.setFolder(newFolder);
                ca.setPath(relativePath.relativize(ca.getRelativePath()).toString());
                coverArtRepository.save(ca);
            });

            // update Root
            mediaFileRepository.findByFolderAndPath(oldFolder, relativePath.toString()).forEach(f -> {
                f.setFolder(newFolder);
                f.setPath("");
                f.setParentPath(null);
                f.setTitle(descendant.getName());
                f.setMediaType(MediaType.DIRECTORY);
                mediaFileRepository.save(f);
            });
        } else {
            // assign descendant to ancestor
            MusicFolder ancestor = newFolder;
            MusicFolder descendant = oldFolder;
            Path relativePath = ancestor.getPath().relativize(descendant.getPath());
            // update root
            mediaFileRepository.findByFolderAndPath(oldFolder, "").forEach(f -> {
                f.setFolder(newFolder);
                f.setTitle(null);
                f.setPath(relativePath.toString());
                f.setParentPath(relativePath.getParent() == null ? "" : relativePath.getParent().toString());
                mediaFileRepository.save(f);
            });

            //update children
            mediaFileRepository.findByFolder(oldFolder).forEach(f -> {
                f.setFolder(newFolder);
                f.setPath(relativePath.resolve(f.getPath()).toString());
                if (StringUtils.hasLength(f.getParentPath())) {
                    f.setParentPath(relativePath.resolve(f.getParentPath()).toString());
                } else {
                    f.setParentPath(relativePath.toString());
                }
                mediaFileRepository.save(f);
            });

            // update cover art
            coverArtRepository.findByFolder(oldFolder).forEach(ca -> {
                ca.setFolder(newFolder);
                ca.setPath(relativePath.resolve(ca.getPath()).toString());
                coverArtRepository.save(ca);
            });
        }
    }

    /**
     * Deletes a music folder. If the music folder is empty, it is deleted. If the music folder has descendants, it is marked as deleted and disabled.
     *
     * @param id Music folder id.
     */
    @Transactional
    public void deleteMusicFolder(Integer id) {

        musicFolderRepository.findByIdAndDeletedFalse(id).ifPresentOrElse(folder -> {
            // if empty folder, just delete
            if (mediaFileRepository.countByFolder(folder) == 0) {
                musicFolderRepository.delete(folder);
                clearMusicFolderCache();
                return;
            }
            Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps = getMusicFolderPathOverlaps(folder, getAllMusicFolders(true, true, true));
            // if folder has ancestors, reassign hierarchy to immediate ancestor and true delete
            if (!overlaps.getMiddle().isEmpty()) {
                reassignChildren(folder, overlaps.getMiddle().getFirst());
                musicFolderRepository.delete(folder);
            } else {
                // if folder has descendants, ignore. they'll stay under descendant hierarchy
                folder.setDeleted(true);
                folder.setEnabled(false);
                musicFolderRepository.save(folder);
            }
            mediaFileRepository.findByFolderAndPresentTrue(folder).forEach(f -> {
                f.setChildrenLastUpdated(Instant.ofEpochMilli(1));
                f.setPresent(false);
                mediaFileRepository.save(f);
            });
            clearMusicFolderCache();
            clearMediaFileCache();
        }, () -> {
                LOG.warn("Could not delete music folder id {}", id);
            });

    }

    /**
     * Enables a music folder. If the music folder is a podcast folder, all other music folders are disabled.
     *
     * @param id Music folder id. Must be a podcast folder.
     * @return True if the music folder was enabled, false otherwise.
     */
    @Transactional
    public boolean enablePodcastFolder(int id) {
        return musicFolderRepository.findByIdAndTypeAndDeletedFalse(id, Type.PODCAST).map(podcastFolder -> {
            try {
                clearMusicFolderCache();
                musicFolderRepository.findByIdNotAndTypeAndDeletedFalse(id, Type.PODCAST).forEach(f -> {
                    f.setEnabled(false);
                    f.setChanged(Instant.now());
                    musicFolderRepository.save(f);
                });
                podcastFolder.setEnabled(true);
                musicFolderRepository.save(podcastFolder);
                return true;
            } catch (Exception e) {
                LOG.warn("Could not enable podcast music folder id {} ({})", podcastFolder.getId(), podcastFolder.getName(), e);
                return false;
            }
        }).orElse(false);
    }

    /**
     * Deletes all music folders that are marked as deleted.
     */
    @Transactional
    public void expunge() {
        musicFolderRepository.deleteAllByDeletedTrue();
    }

    /**
     * Updates a music folder by info. The id must be set. If the path is changed, the new path must not overlap with any existing music folder.
     *
     * @param info Music folder info.
     */
    @Transactional
    public void updateMusicFolderByInfo(MusicFolderInfo info) {
        if (info.getId() == null) {
            throw new IllegalArgumentException("Music folder id must be set.");
        }
        MusicFolder musicFolder = info.toMusicFolder();
        if (musicFolder != null) {
            musicFolderRepository.findByIdAndDeletedFalse(info.getId())
                .filter(folder -> {
                    if (folder.getPath().equals(musicFolder.getPath())) {
                        return true;
                    } else {
                        Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps = getMusicFolderPathOverlaps(musicFolder, getAllMusicFolders(true, true, true).stream().filter(f -> !f.getId().equals(musicFolder.getId())).toList());
                        if (overlaps.getLeft().isEmpty() && overlaps.getMiddle().isEmpty() && overlaps.getRight().isEmpty()) {
                            return true;
                        } else {
                            LOG.warn("Music folder with path {} overlaps with existing music folder path(s) ({}) and can therefore not be updated.", musicFolder.getPath(), logMusicFolderOverlap(overlaps));
                            return false;
                        }
                    }
                })
                .ifPresentOrElse(f -> {
                    f.setName(musicFolder.getName());
                    f.setPath(musicFolder.getPath());
                    f.setType(musicFolder.getType());
                    f.setEnabled(musicFolder.isEnabled());
                    f.setDeleted(musicFolder.isDeleted());
                    musicFolderRepository.save(f);
                    clearMusicFolderCache();
                }, () -> {
                        throw new IllegalArgumentException("Music folder with path " + musicFolder.getPath() + " overlaps with existing music folder path(s) and can therefore not be updated.");
                    });
        }
    }

    public List<MusicFolder> getDeletedMusicFolders() {
        return musicFolderRepository.findByDeleted(true);
    }

    /**
     * @return List of overlaps
     * <ul>
     * <li>List 1: Exact path overlaps (in no order)
     * <li>List 2: Ancestors of the given folder (closest ancestor first: /a/b/c -> [/a/b, /a])
     * <li>List 3: Descendants of the given folder (closest descendant first: /a/b/c -> [/a/b/c/d, /a/b/c/e, /a/b/c/d/f])
     * </ul>
     */
    public static Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> getMusicFolderPathOverlaps(MusicFolder folder, List<MusicFolder> allFolders) {
        Path absoluteFolderPath = folder.getPath().normalize().toAbsolutePath();
        List<MusicFolder> sameFolders = allFolders.parallelStream().filter(f -> {
            // is same but not itself
            Path fAbsolute = f.getPath().normalize().toAbsolutePath();
            return fAbsolute.equals(absoluteFolderPath) && !f.getId().equals(folder.getId());
        }).toList();
        List<MusicFolder> ancestorFolders = allFolders.parallelStream().filter(f -> {
            // is ancestor
            Path fAbsolute = f.getPath().normalize().toAbsolutePath();
            return absoluteFolderPath.getNameCount() > fAbsolute.getNameCount()
                    && absoluteFolderPath.startsWith(fAbsolute);
        }).sorted(Comparator.comparing(f -> f.getPath().getNameCount(), Comparator.reverseOrder())).toList();
        List<MusicFolder> descendantFolders = allFolders.parallelStream().filter(f -> {
            // is descendant
            Path fAbsolute = f.getPath().normalize().toAbsolutePath();
            return fAbsolute.getNameCount() > absoluteFolderPath.getNameCount()
                    && fAbsolute.startsWith(absoluteFolderPath);
        }).sorted(Comparator.comparing(f -> f.getPath().getNameCount())).toList();

        return Triple.of(sameFolders, ancestorFolders, descendantFolders);
    }

    public static String logMusicFolderOverlap(Triple<List<MusicFolder>, List<MusicFolder>, List<MusicFolder>> overlaps) {
        StringBuilder result = new StringBuilder("SAME: ");
        result.append(overlaps.getLeft().stream().map(f -> f.getName()).collect(joining(",", "[", "]")));
        result.append(", ANCESTOR: ");
        result.append(overlaps.getMiddle().stream().map(f -> f.getName()).collect(joining(",", "[", "]")));
        result.append(", DESCENDANT: ");
        result.append(overlaps.getRight().stream().map(f -> f.getName()).collect(joining(",", "[", "]")));

        return result.toString();
    }

    public void clearMusicFolderCache() {
        cachedMusicFolders = null;
        cachedMusicFoldersPerUser.clear();
    }

    @CacheEvict(cacheNames = { "mediaFilePathCache", "mediaFileIdCache" }, allEntries = true)
    public void clearMediaFileCache() {
        // TODO: optimize cache eviction
    }

    /**
     * Returns the music folder that contains the given file. If multiple music folders contain the file, the one with the longest path is returned.
     *
     * @param file             File to get music folder for.
     * @param includeDisabled Whether to include disabled folders.
     * @param includeNonExisting Whether to include non-existing folders.
     * @return Music folder that contains the file, or null if no music folder contains the file.
     */
    public Optional<MusicFolder> getMusicFolderForFile(Path file, boolean includeDisabled, boolean includeNonExisting) {
        return getAllMusicFolders(includeDisabled, includeNonExisting).stream()
                .filter(folder -> FileUtil.isFileInFolder(file, folder.getPath()))
                .sorted(Comparator.comparing(folder -> folder.getPath().getNameCount(), Comparator.reverseOrder()))
                .findFirst();
    }

    /**
     * Returns the music folder that contains the given file. If multiple music folders contain the file, the one with the longest path is returned.
     *
     * @param file File to get music folder for.
     * @return Music folder that contains the file, or null if no music folder contains the file.
     */
    public MusicFolder getMusicFolderForFile(Path file) {
        return getMusicFolderForFile(file, false, true).orElse(null);
    }
}
