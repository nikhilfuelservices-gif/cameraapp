package com.example.cameraapp

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

data class CaptureRecord(
    val id: String,
    val sequence: Long,
    val capturedEpochMs: Long,
    val timestampText: String,
    val photoFile: String,
    val photoSha256: String,
    val previousHash: String,
    val signatureB64: String,
    val recordHash: String
)

data class VerifiedPhoto(
    val record: CaptureRecord,
    val file: File,
    val signatureValid: Boolean,
    val hashValid: Boolean,
    val chainValid: Boolean,
    val recordHashValid: Boolean
) {
    val authentic: Boolean get() = signatureValid && hashValid && chainValid && recordHashValid
}

class SecurityStore(context: Context) {
    private val root = File(context.filesDir, "authenticated").apply { mkdirs() }
    private val recordDir = File(root, "records").apply { mkdirs() }
    private val photoDir = File(root, "photos").apply { mkdirs() }
    private val alias = "CameraAppLocalSigningKeyV1"

    init { ensureKey() }

    fun photoDir(): File = photoDir

    @Synchronized
    fun nextSequence(): Long =
        recordDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            ?.maxOrNull()?.plus(1L) ?: 1L

    @Synchronized
    fun createRecord(photoFile: File, epochMs: Long, timestampText: String): CaptureRecord {
        val sequence = nextSequence()
        val id = "CAM-%06d".format(sequence)
        val photoHash = sha256(photoFile.readBytes())
        val previousHash = recordDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull(::readRecord)?.maxByOrNull { it.sequence }?.recordHash ?: ""
        val canonical = canonical(id, sequence, epochMs, timestampText, photoFile.name, photoHash, previousHash)
        val canonicalBytes = canonical.toByteArray(StandardCharsets.UTF_8)
        val signature = sign(canonicalBytes)
        val signatureB64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        val recHash = recordHash(canonicalBytes, signature)
        val record = CaptureRecord(
            id, sequence, epochMs, timestampText, photoFile.name, photoHash, previousHash,
            signatureB64, recHash
        )
        val json = JSONObject().apply {
            put("version", 1)
            put("id", record.id)
            put("sequence", record.sequence)
            put("capturedEpochMs", record.capturedEpochMs)
            put("timestampText", record.timestampText)
            put("photoFile", record.photoFile)
            put("photoSha256", record.photoSha256)
            put("previousHash", record.previousHash)
            put("signatureB64", record.signatureB64)
            put("recordHash", record.recordHash)
        }
        File(recordDir, "${record.sequence}.json").writeText(json.toString())
        return record
    }

    fun listVerified(): List<VerifiedPhoto> {
        val records = recordDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull(::readRecord)?.sortedBy { it.sequence } ?: emptyList()
        var expectedPrevious = ""
        val output = ArrayList<VerifiedPhoto>(records.size)
        for (record in records) {
            val file = File(photoDir, record.photoFile)
            val hashValid = file.exists() && sha256(file.readBytes()) == record.photoSha256
            val signatureBytes = try { Base64.decode(record.signatureB64, Base64.NO_WRAP) } catch (_: Exception) { ByteArray(0) }
            val canonicalBytes = canonical(
                record.id, record.sequence, record.capturedEpochMs, record.timestampText,
                record.photoFile, record.photoSha256, record.previousHash
            ).toByteArray(StandardCharsets.UTF_8)
            val sigValid = try { verify(canonicalBytes, signatureBytes) } catch (_: Exception) { false }
            val actualRecordHash = if (signatureBytes.isNotEmpty()) recordHash(canonicalBytes, signatureBytes) else ""
            val recordHashValid = actualRecordHash.isNotEmpty() && actualRecordHash == record.recordHash
            val chainValid = sigValid && record.previousHash == expectedPrevious
            expectedPrevious = record.recordHash
            output += VerifiedPhoto(record, file, sigValid, hashValid, chainValid, recordHashValid)
        }
        return output.asReversed()
    }

    private fun readRecord(file: File): CaptureRecord? = try {
        val j = JSONObject(file.readText())
        CaptureRecord(
            j.getString("id"), j.getLong("sequence"), j.getLong("capturedEpochMs"),
            j.getString("timestampText"), j.getString("photoFile"), j.getString("photoSha256"),
            j.getString("previousHash"), j.getString("signatureB64"), j.getString("recordHash")
        )
    } catch (_: Exception) { null }

    private fun ensureKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
    }

    private fun sign(data: ByteArray): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry).privateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key); update(data); sign()
        }
    }

    private fun verify(data: ByteArray, signature: ByteArray): Boolean {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val publicKey = ks.getCertificate(alias)?.publicKey ?: return false
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey); update(data); verify(signature)
        }
    }

    private fun canonical(
        id: String, sequence: Long, epochMs: Long, timestampText: String,
        photoFile: String, photoSha256: String, previousHash: String
    ) = listOf(
        id, sequence.toString(), epochMs.toString(), timestampText,
        photoFile, photoSha256, previousHash
    ).joinToString("
")

    private fun recordHash(canonicalBytes: ByteArray, signature: ByteArray): String =
        sha256(canonicalBytes + signature)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
