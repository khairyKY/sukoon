package com.sukoon.app.data.source.libre

/**
 * Deliberately NOT implemented here.
 *
 * Turning a Libre 2's UID + patch info into the key that unlocks its encrypted BLE stream is the
 * one genuinely Abbott-proprietary step in this whole pipeline. The community has published
 * working implementations (xDrip+'s OOP2 integration, Juggluco), but per the licensing decision
 * in docs/PLAN.md §1, we don't copy that code — we clean-room reimplement from the publicly
 * documented method, and only when it's actually needed for the BLE-stream-decoding task, not
 * guessed at here just to make this class look complete. Getting this wrong silently would
 * produce plausible-looking but wrong glucose numbers, which is a real patient-safety risk, not
 * just a bug — so [UnimplementedLibreKeyDerivation] fails loudly instead.
 *
 * This interface is the seam where that work plugs in, so LibreNfcSession's (real, working) NFC
 * layer doesn't need to change shape when it does.
 */
fun interface LibreKeyDerivation {
    fun deriveKey(uid: ByteArray, patchInfo: ByteArray): ByteArray
}

object UnimplementedLibreKeyDerivation : LibreKeyDerivation {
    override fun deriveKey(uid: ByteArray, patchInfo: ByteArray): ByteArray {
        throw NotImplementedError(
            "Libre 2 key derivation is not yet implemented — see the KDoc on LibreKeyDerivation " +
                "for why, and docs/PLAN.md §1 for the clean-room-reimplementation plan.",
        )
    }
}
