package hoo.complicationswatchface

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.ProviderChooserIntent
import android.support.wearable.complications.ProviderInfoRetriever
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import java.util.concurrent.Executors

class ConfigActivity : Activity(), View.OnClickListener {

    companion object {
        private val TAG = "ConfigActivity"
        private val COMPLICATION_CONFIG_REQUEST_CODE = 1001

        private val COMPLICATION_BG_IDS =
                intArrayOf(
                        R.id.complication_top_bg,
                        R.id.complication_top_right_bg,
                        R.id.complication_bottom_right_bg,
                        R.id.complication_bottom_bg,
                        R.id.complication_bottom_left_bg,
                        R.id.complication_top_left_bg,
                        R.id.complication_count_bg)

        private val COMPLICATION_BTN_IDS =
                intArrayOf(
                        R.id.complication_top,
                        R.id.complication_top_right,
                        R.id.complication_bottom_right,
                        R.id.complication_bottom,
                        R.id.complication_bottom_left,
                        R.id.complication_top_left,
                        R.id.complication_count)
    }

    private var mComplicationIds: IntArray = DigitalWatchFaceService.getComplicationIds()

    private var mDefaultAddComplicationDrawable: Drawable? = null

    // Selected complication id by user.
    private var mSelectedComplicationId: Int = 0

    // ComponentName used to identify a specific service that renders the watch face.
    private var mWatchFaceComponentName: ComponentName? = null

    private var mComplicationBackgroundSparseArray: SparseArray<ImageView>? = null
    private var mComplicationButtonSparseArray: SparseArray<ImageButton>? = null

    // Required to retrieve complication data from watch face for preview.
    private var mProviderInfoRetriever: ProviderInfoRetriever? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_config)

        mDefaultAddComplicationDrawable = getDrawable(R.drawable.add_complication)

        // TODO: Step 3, initialize 1
        mSelectedComplicationId = -1

        mWatchFaceComponentName = ComponentName(applicationContext, DigitalWatchFaceService::class.java)

        mComplicationBackgroundSparseArray = SparseArray(mComplicationIds.size)
        mComplicationButtonSparseArray = SparseArray(mComplicationIds.size)

        for (i in mComplicationIds.indices) {
            val complicationId: Int = mComplicationIds[i]

            // Sets up complication preview.
            val complicationBackground: ImageView = findViewById(COMPLICATION_BG_IDS[i])
            complicationBackground.visibility = View.INVISIBLE
            mComplicationBackgroundSparseArray?.put(complicationId, complicationBackground)

            // Sets default as "Add Complication" icon.
            val complicationButton: ImageButton = findViewById(COMPLICATION_BTN_IDS[i])
            complicationButton.setOnClickListener(this)
            complicationButton.setImageDrawable(mDefaultAddComplicationDrawable)
            mComplicationButtonSparseArray?.put(complicationId, complicationButton)
        }

        // TODO: Step 3, initialize 2
        // Initialization of code to retrieve active complication data for the watch face.
        mProviderInfoRetriever = ProviderInfoRetriever(applicationContext, Executors.newCachedThreadPool())
        mProviderInfoRetriever?.init()

        retrieveInitialComplicationsData()
    }

    override fun onDestroy() {
        super.onDestroy()

        // TODO: Step 3, release
        // Required to release retriever for active complication data.
        mProviderInfoRetriever?.release()
    }

    // TODO: Step 3, retrieve complication data
    private fun retrieveInitialComplicationsData() {
        val complicationIds = DigitalWatchFaceService.getComplicationIds()

        mProviderInfoRetriever?.retrieveProviderInfo(
                object : ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    override fun onProviderInfoReceived(
                            watchFaceComplicationId: Int,
                            complicationProviderInfo: ComplicationProviderInfo?) {

                        Log.d(TAG, "onProviderInfoReceived: " + (complicationProviderInfo ?: "null"))

                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo)
                    }
                },
                mWatchFaceComponentName,
                *complicationIds)
    }

    override fun onClick(view: View) {
        for (i in mComplicationIds.indices) {
            val complicationId: Int = mComplicationIds[i]
            val complicationButton: ImageButton = mComplicationButtonSparseArray!!.get(complicationId)

            if (view == complicationButton) {
                Log.d(TAG, "Complication Button click() id:" + complicationId)
                launchComplicationHelperActivity(complicationId)
                break
            }
        }
    }

    // Verifies the watch face supports the complication location, then launches the helper
    // class, so user can choose their complication data provider.
    // TODO: Step 3, launch data selector
    private fun launchComplicationHelperActivity(complicationId: Int) {
        mSelectedComplicationId = complicationId

        if (mSelectedComplicationId >= 0) {
            val supportedTypes = DigitalWatchFaceService.getSupportedComplicationTypes(
                    mSelectedComplicationId)

            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            applicationContext,
                            mWatchFaceComponentName,
                            mSelectedComplicationId,
                            *supportedTypes),
                    COMPLICATION_CONFIG_REQUEST_CODE)
        } else {
            Log.d(TAG, "Complication not supported by watch face.")
        }
    }

    fun updateComplicationViews(
            watchFaceComplicationId: Int, complicationProviderInfo: ComplicationProviderInfo?) {
        Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId)
        Log.d(TAG, "\tinfo: " + (complicationProviderInfo ?: "") )

        if (watchFaceComplicationId >= 0) {
            val complicationButton: ImageButton = mComplicationButtonSparseArray!!.get(watchFaceComplicationId)
            val complicationBackground: ImageView = mComplicationBackgroundSparseArray!!.get(watchFaceComplicationId)

            if (complicationProviderInfo != null) {
                complicationButton.setImageIcon(complicationProviderInfo.providerIcon)
                complicationBackground.visibility = View.VISIBLE

            } else {
                complicationButton.setImageDrawable(mDefaultAddComplicationDrawable)
                complicationBackground.visibility = View.INVISIBLE
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // TODO: Step 3, update views
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {

            // Retrieves information for selected Complication provider.
            val complicationProviderInfo = data.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            Log.d(TAG, "Provider: " + complicationProviderInfo)

            if (mSelectedComplicationId >= 0) {
                updateComplicationViews(mSelectedComplicationId, complicationProviderInfo)
            }
        }
    }
}
