package com.drivest.navigation.subscription

import org.junit.Assert.assertTrue
import org.junit.Test

class StoreSubscriptionLinksTest {

    @Test
    fun playSubscriptionsUrlIsConfigured() {
        assertTrue(
            StoreSubscriptionLinks.PLAY_SUBSCRIPTIONS_URL.contains(
                "play.google.com/store/account/subscriptions"
            )
        )
    }
}
