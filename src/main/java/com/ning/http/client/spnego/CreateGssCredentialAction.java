package com.ning.http.client.spnego;

import java.security.PrivilegedExceptionAction;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

class CreateGssCredentialAction implements PrivilegedExceptionAction<GSSCredential> {

    private final GSSManager gssManager;
    private final Oid negotiationOid;

    CreateGssCredentialAction(GSSManager gssManager, Oid negotiationOid) {
        this.gssManager = gssManager;
        this.negotiationOid = negotiationOid;
    }

    @Override
    public GSSCredential run() throws Exception {
        return gssManager
                .createCredential(null, GSSCredential.DEFAULT_LIFETIME, negotiationOid, GSSCredential.INITIATE_AND_ACCEPT);
    }
}
