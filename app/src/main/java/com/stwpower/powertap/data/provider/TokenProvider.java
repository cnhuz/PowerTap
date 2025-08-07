package com.stwpower.powertap.data.provider;

import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;

public class TokenProvider implements ConnectionTokenProvider {

    @Override
    public void fetchConnectionToken(ConnectionTokenCallback callback) {
        try {
            final String token = MyApiClient.createConnectionToken();
            callback.onSuccess(token);
        } catch (ConnectionTokenException e) {
            callback.onFailure(e);
        }
    }
}