package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Love Walkie Talkie", appName)
  }

  @Test
  fun `room code generator produces valid formatted codes`() {
    val code = com.example.data.repository.WalkieTalkieRepository.generateSecureRoomCode()
    org.junit.Assert.assertNotNull(code)
    org.junit.Assert.assertTrue(code.contains("-"))
    org.junit.Assert.assertTrue(code.length >= 6)
  }
}
