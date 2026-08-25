package com.studymate.sm.cid.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Klien sederhana untuk memanggil Gemini API (model gemini-flash-latest, alias resmi
 * Google yang selalu mengarah ke model Flash terbaru) lewat Google AI Studio.
 * API key disimpan pengguna sendiri di Pengaturan (format terbaru diawali "AQ.").
 *
 * AI Asisten berperan sebagai tutor belajar, bukan mesin penjawab soal instan,
 * sehingga system instruction diarahkan untuk membimbing pemahaman konsep.
 */
class GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val systemInstruction = """
        Kamu adalah AI Asisten di aplikasi Study Mate. Tugasmu membantu pelajar memahami
        materi pelajaran mereka, bukan memberi jawaban instan untuk tugas atau ujian.
        Jelaskan konsep secara bertahap, gunakan bahasa sederhana, berikan contoh bila perlu,
        dan ajak pengguna berpikir dengan pertanyaan pemandu. Jika pengguna hanya meminta
        jawaban langsung untuk PR atau soal ujian, arahkan mereka untuk memahami konsepnya
        terlebih dahulu alih-alih memberi jawaban akhir secara langsung.
    """.trimIndent()

    suspend fun kirimPesan(apiKey: String, konteksMateri: String?, pesanPengguna: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("API key Gemini belum diatur. Buka Pengaturan untuk memasang API key dari Google AI Studio."))
                }

                val promptLengkap = buildString {
                    append(systemInstruction)
                    append("\n\n")
                    if (!konteksMateri.isNullOrBlank()) {
                        append("Konteks materi pengguna:\n$konteksMateri\n\n")
                    }
                    append("Pertanyaan pengguna: $pesanPengguna")
                }

                val bodyJson = JsonObject().apply {
                    add("contents", JsonParser.parseString(
                        """[{"role":"user","parts":[{"text": ${gsonEscape(promptLengkap)} }]}]"""
                    ))
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val pesan = when (response.code) {
                            400, 401, 403 -> "API key ditolak (${response.code}). Pastikan key masih aktif dan formatnya benar (key terbaru Google diawali \"AQ.\")."
                            404 -> "Model AI tidak ditemukan (404). Coba lagi nanti, kemungkinan ada perubahan di sisi Google."
                            429 -> "Terlalu banyak permintaan ke Gemini (429). Tunggu sebentar lalu coba lagi."
                            in 500..599 -> "Server Gemini sedang bermasalah (${response.code}). Coba lagi beberapa saat lagi."
                            else -> "Gagal menghubungi Gemini (${response.code}): $bodyString"
                        }
                        return@withContext Result.failure(Exception(pesan))
                    }
                    val json = JsonParser.parseString(bodyString).asJsonObject
                    val text = json.getAsJsonArray("candidates")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString
                        ?: "Maaf, AI Asisten tidak bisa memberi jawaban saat ini."
                    Result.success(text)
                }
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception("Koneksi ke Gemini timeout. Periksa koneksi internet kamu (pastikan mode pesawat mati) lalu coba lagi."))
            } catch (e: UnknownHostException) {
                Result.failure(Exception("Tidak bisa terhubung ke internet. Periksa koneksi Wi-Fi/data seluler kamu."))
            } catch (e: IOException) {
                Result.failure(Exception("Terjadi masalah jaringan: ${e.message ?: "tidak diketahui"}. Coba lagi."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun gsonEscape(text: String): String {
        return com.google.gson.Gson().toJson(text)
    }

    companion object {
        private const val MODEL = "gemini-flash-latest"
    }
}
