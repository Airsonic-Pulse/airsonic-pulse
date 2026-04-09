package org.airsonic.player.util;

import org.airsonic.player.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class UserUtil {

    private final static Logger LOG = LoggerFactory.getLogger(UserUtil.class);

    /**
     * Get system administrator user, which system operations perform on behalf.
     *
     * @param users all users.
     * @return system admin.
     */
    public static User getSysAdmin(List<User> users) {
        LOG.debug("getSysAdmin");
        if (CollectionUtils.isEmpty(users)) {
            LOG.debug("getSysAdmin: users is empty");
            return null;
        }

        List<User> admins = users.stream()
                .filter(User::isAdminRole).toList();

        return admins.stream()
                .filter(user -> User.USERNAME_ADMIN.equals(user.getUsername()))
                .findAny()
                .orElseGet(() -> admins.getFirst());
    }

}
