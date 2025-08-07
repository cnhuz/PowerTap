package com.stwpower.powertap.data.provider

import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stwpower.powertap.data.api.MyApiClient

/**
 * Stripe Terminal 连接令牌提供者
 */
class TokenProvider : ConnectionTokenProvider {

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        try {
            val token = MyApiClient.createConnectionToken()
            callback.onSuccess(token)
        } catch (e: ConnectionTokenException) {
            callback.onFailure(e)
        }
    }
}
