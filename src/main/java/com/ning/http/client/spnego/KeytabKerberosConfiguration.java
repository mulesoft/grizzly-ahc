package com.ning.http.client.spnego;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

/**
 * Kerberos' configuration that uses a keytab file.
 */
class KeytabKerberosConfiguration extends Configuration {

    private final String principal;
    private final String keytabFileName;

    public KeytabKerberosConfiguration(String principal, String keytabFileName) {
        this.principal = principal;
        this.keytabFileName = keytabFileName;
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        Map<String, String> options = new HashMap<>();
        options.put("keyTab", keytabFileName);
        options.put("principal", principal);
        options.put("useKeyTab", "true");
        options.put("storeKey", "true");
        options.put("doNotPrompt", "true");
        options.put("useTicketCache", "true");
        options.put("renewTGT", "true");
        options.put("refreshKrb5Config", "true");
        options.put("isInitiator", "true");
        String ticketCache = System.getenv("KRB5CCNAME");
        if (ticketCache != null) {
            options.put("ticketCache", ticketCache);
        }
        options.put("debug", "true");

        return new AppConfigurationEntry[]{
                new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options),};
    }
}
