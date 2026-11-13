package com.farol.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.farol.app.data.Account
import com.farol.app.ui.ConsentActivity
import com.farol.app.ui.LoginActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Abre as telas de entrada e de privacidade num Android de verdade: não podem travar. */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun telaDeLoginAbre() {
        Account.clear(ctx)
        ActivityScenario.launch(LoginActivity::class.java).use { s ->
            assertTrue(s.state.isAtLeast(Lifecycle.State.CREATED))
        }
    }

    @Test fun telaDePrivacidadeAbre() {
        ActivityScenario.launch(ConsentActivity::class.java).use { s ->
            assertTrue(s.state.isAtLeast(Lifecycle.State.CREATED))
        }
    }

    @Test fun semAceiteNaoPassa() {
        assertFalse(Account.consentOk(ctx))
        assertTrue(Account.policyUrl(ctx).endsWith("/privacidade"))
    }
}
