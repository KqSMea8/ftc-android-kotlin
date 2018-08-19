package com.ft.ftchinese.models

import android.util.Log
import com.ft.ftchinese.util.ApiEndpoint
import com.ft.ftchinese.util.Fetch
import com.ft.ftchinese.util.gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.experimental.async
import java.io.IOException

data class Account(
        val email: String,
        val password: String
) {
    suspend fun send (url: String): User? {
        val job = async {
            Fetch.post(url, gson.toJson(this@Account))
        }

        return try {
            val response = job.await() ?: return null

            val body = response.body()?.string()

            gson.fromJson<User>(body, User::class.java)
        } catch (e: IOException) {
            Log.w(TAG, "Response error: $e")
            null
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "JSON parse error: $e")
            null
        } catch (e: ErrorResponse) {
            throw e
        }
    }

    suspend fun login(): User? {
        return send(ApiEndpoint.LOGIN)
    }

    suspend fun create(): User? {
        return send(ApiEndpoint.NEW_ACCOUNT)
    }

    companion object {
        private const val TAG = "Account"
    }
}