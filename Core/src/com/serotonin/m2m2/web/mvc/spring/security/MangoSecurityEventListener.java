/**
 * Copyright (C) 2018 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.web.mvc.spring.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent;
import org.springframework.stereotype.Component;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.UserDao;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.SystemEventType;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * Class to handle all security related events.
 *
 * @author Terry Packer
 */
@Component
public class MangoSecurityEventListener {

    private static final Log LOG = LogFactory.getLog(MangoSecurityEventListener.class);
    private final UserDao userDao;

    @Autowired
    public MangoSecurityEventListener(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Listen for events generated by the MangoSwitchUserFilter
     *
     * @param event
     */
    @EventListener
    private void handleAuthenticationSwitchUserEvent(AuthenticationSwitchUserEvent event) {
        LOG.info("Switch User: '" + ((PermissionHolder) event.getAuthentication().getPrincipal()).getPermissionHolderName() + "' switched to '" + event.getTargetUser().getUsername() + "'");
    }

    @EventListener
    private void handleAuthenticationSuccessEvent(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        PermissionHolder principal = (PermissionHolder) authentication.getPrincipal();
        User user = principal.getUser();
        Object details = authentication.getDetails();

        String remoteAddress = "";
        boolean recordLogin = true;

        if (details instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails webDetails = (WebAuthenticationDetails) details;
            remoteAddress = webDetails.getRemoteAddress();
        }
        if (details instanceof MangoAuthenticationDetails) {
            MangoAuthenticationDetails mangoDetails = (MangoAuthenticationDetails) details;
            recordLogin = mangoDetails.isRecordLogin();
        }

        if (user != null && recordLogin) {
            // Update the last login time.
            userDao.recordLogin(user);

            SystemEventType eventType = new SystemEventType(SystemEventType.TYPE_USER_LOGIN, user.getId());
            TranslatableMessage message = new TranslatableMessage("event.login", user.getUsername(), remoteAddress);

            // this event used to "return to normal" when the user logged out by listening to the MangoHttpSessionDestroyedEvent
            // problem was that a) this event was only fired when the user explicitly logged out via the logout button,
            // and not when a session expired b) It returned all login events for the user to normal, not just the one
            // that corresponded to the session that created the login event
            SystemEventType.raiseEvent(eventType, Common.timer.currentTimeMillis(), false, message);
        }
    }
}
