package com.google.android.apps.muzei.gallery

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.ShareCompat
import android.util.Log
import androidx.core.widget.toast
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch

/**
 * Activity which responds to [android.content.Intent.ACTION_SEND] and
 * [android.content.Intent.ACTION_SEND_MULTIPLE] to add one or more
 * images to the Gallery
 */
class GalleryAddPhotosActivity : Activity() {

    companion object {
        private const val TAG = "GalleryAddPhotos"
    }

    private var streamCount: Int = 0
    private var successCount: Int = 0
    private var failureCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentReader = ShareCompat.IntentReader.from(this)
        if (!intentReader.isShareIntent) {
            finish()
            return
        }
        val callingApplication = getCallingApplication(intentReader)
        streamCount = intentReader.streamCount
        for (index in 0 until streamCount) {
            val photoUri = intentReader.getStream(index)
            val chosenPhoto = ChosenPhoto(photoUri)

            launch(UI) {
                val context = this@GalleryAddPhotosActivity
                val id = async {
                    GalleryDatabase.getInstance(context).chosenPhotoDao()
                        .insert(context, chosenPhoto, callingApplication)
                }.await()
                if (id == 0L) {
                    Log.e(TAG, "Unable to insert chosen artwork for $photoUri")
                    failureCount++
                } else {
                    successCount++
                }
                updateCount()
            }
        }
    }

    private fun getCallingApplication(intentReader: ShareCompat.IntentReader): String? {
        val callingPackage = intentReader.callingPackage ?: ActivityCompat.getReferrer(this)?.host
        return callingPackage?.run {
            return try {
                val pm = packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(callingPackage, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not retrieve label for package $this", e)
                null
            }
        }
    }

    private fun updateCount() {
        if (successCount + failureCount == streamCount) {
            toast(if (failureCount == 0) {
                resources.getQuantityString(R.plurals.gallery_add_photos_success,
                        successCount, successCount)
            } else {
                resources.getQuantityString(R.plurals.gallery_add_photos_failure,
                        failureCount, failureCount, streamCount)
            })
            finish()
        }
    }
}
