package com.example.imgbase.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object SupabaseRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Result 클래스들
    sealed class UploadResult {
        object Success : UploadResult()
        data class Failure(val errorMessage: String) : UploadResult()
    }

    sealed class ListResult {
        data class Success(val items: List<String>) : ListResult()
        data class Failure(val errorMessage: String) : ListResult()
    }

    sealed class DeleteResult {
        object Success : DeleteResult()
        data class Failure(val errorMessage: String) : DeleteResult()
    }

    // 1. 파일 업로드 (사용자)
    suspend fun uploadImage(fileName: String, fileBytes: ByteArray, mimeType: String = "image/jpeg"): UploadResult {
        val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.BUCKET_NAME}/$fileName"
        val requestBody: RequestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        UploadResult.Success
                    }
                    response.code == 401 -> {
                        UploadResult.Failure("인증 실패: API 키를 확인해주세요")
                    }
                    response.code == 403 -> {
                        UploadResult.Failure("권한 없음: 버킷 정책을 확인해주세요")
                    }
                    response.code == 404 -> {
                        UploadResult.Failure("버킷을 찾을 수 없습니다")
                    }
                    response.code == 413 -> {
                        UploadResult.Failure("파일이 너무 큽니다")
                    }
                    response.code == 409 -> {
                        UploadResult.Failure("동일한 이름의 파일이 이미 존재합니다")
                    }
                    response.code >= 500 -> {
                        UploadResult.Failure("서버 오류: 잠시 후 다시 시도해주세요")
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: "알 수 없는 오류"
                        UploadResult.Failure("업로드 실패 (${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure("네트워크 오류: ${e.message ?: "인터넷 연결을 확인해주세요"}")
        } catch (e: Exception) {
            UploadResult.Failure("예상치 못한 오류: ${e.message ?: "다시 시도해주세요"}")
        }
    }

    // 2. 파일 목록 조회 (관리자)
    suspend fun listImages(): ListResult {
        val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/list/${SupabaseConfig.BUCKET_NAME}"
        val requestBody = """
        {
          "prefix": "",
          "limit": 100,
          "offset": 0
        }
        """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string()
                        if (body.isNullOrEmpty()) {
                            return ListResult.Failure("빈 응답을 받았습니다")
                        }

                        try {
                            val jsonArray = JSONArray(body)
                            val list = List(jsonArray.length()) { i ->
                                val obj = jsonArray.getJSONObject(i)
                                obj.getString("name")
                            }
                            ListResult.Success(list)
                        } catch (e: Exception) {
                            ListResult.Failure("응답 파싱 실패: ${e.message}")
                        }
                    }
                    response.code == 401 -> {
                        ListResult.Failure("인증 실패: API 키를 확인해주세요")
                    }
                    response.code == 403 -> {
                        ListResult.Failure("권한 없음: 버킷 읽기 권한을 확인해주세요")
                    }
                    response.code == 404 -> {
                        ListResult.Failure("버킷을 찾을 수 없습니다: ${SupabaseConfig.BUCKET_NAME}")
                    }
                    response.code >= 500 -> {
                        ListResult.Failure("서버 오류 (${response.code}): 잠시 후 다시 시도해주세요")
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: ""
                        ListResult.Failure("목록 조회 실패 (${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            ListResult.Failure("네트워크 오류: ${e.message ?: "인터넷 연결을 확인해주세요"}")
        } catch (e: Exception) {
            ListResult.Failure("예상치 못한 오류: ${e.message ?: "다시 시도해주세요"}")
        }
    }

    // 3. 파일 삭제 (관리자)
    suspend fun deleteImages(fileNames: List<String>): DeleteResult {
        if (fileNames.isEmpty()) return DeleteResult.Success

        val url = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/${SupabaseConfig.BUCKET_NAME}"
        val payload = JSONObject().apply {
            put("prefixes", JSONArray(fileNames))
        }.toString()
        val requestBody = payload.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY) // apikey 헤더 추가
            .delete(requestBody) // DELETE 메소드 사용
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        DeleteResult.Success
                    }
                    response.code == 401 -> {
                        DeleteResult.Failure("인증 실패: API 키를 확인해주세요")
                    }
                    response.code == 403 -> {
                        DeleteResult.Failure("권한 없음: 삭제 권한을 확인해주세요")
                    }
                    response.code == 404 -> {
                        DeleteResult.Failure("삭제할 파일을 찾을 수 없습니다")
                    }
                    response.code >= 500 -> {
                        DeleteResult.Failure("서버 오류: 잠시 후 다시 시도해주세요")
                    }
                    else -> {
                        val errorBody = response.body?.string() ?: ""
                        DeleteResult.Failure("삭제 실패 (${response.code}): $errorBody")
                    }
                }
            }
        } catch (e: IOException) {
            DeleteResult.Failure("네트워크 오류: ${e.message ?: "인터넷 연결을 확인해주세요"}")
        } catch (e: Exception) {
            DeleteResult.Failure("예상치 못한 오류: ${e.message ?: "다시 시도해주세요"}")
        }
    }

}