package com.fsck.k9.mail.oauth;


import java.util.List;

import com.fsck.k9.mail.AuthenticationFailedException;


public interface OAuth2TokenProvider {
    /**
     * A default timeout value to use when fetching tokens.
     */
    int OAUTH2_TIMEOUT = 30000;

    
    /**
     * @return Accounts suitable for OAuth 2.0 token provision.
     */
    List<String> getAccounts();

    /**
     * Fetch a token. No guarantees are provided for validity.
     */
    String getToken(String username, long timeoutMillis) throws AuthenticationFailedException;

    /**
     * Invalidate the token for this username.
     *
     * <p>
     * Note that the token should always be invalidated on credential failure. However invalidating a token every
     * single time is not recommended.
     * <p>
     * Invalidating a token and then failure with a new token should be treated as a permanent failure.
     */
    void invalidateToken(String username);
}
