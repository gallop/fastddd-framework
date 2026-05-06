package com.gallop.fastddd.security.provider;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Bridge between Spring Security and CRUD module's CurrentUserProvider.
 * Returns the authenticated username instead of "system".
 */
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }
}
