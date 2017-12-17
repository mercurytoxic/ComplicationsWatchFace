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
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import java.util.concurrent.Executors

class ConfigActivity : Activity(), View.OnClickListener {

    companion object {
        private val TAG = "ConfigActivity"
        private val COMPLICATION_CONFIG_REQUEST_CODE = 1001
    }

    // Selected complication id by user.
    private var mSelectedComplicationId: Int = 0

    // ComponentName used to identify a specific service that renders the watch face.
    private var mWatchFaceComponentName: ComponentName? = null

    // Required to retrieve complication data from watch face for preview.
    private var mProviderInfoRetriever: ProviderInfoRetriever? = null

    private var mLeftComplicationBackground: ImageView? = null
    private var mRightComplicationBackground: ImageView? = null

    private var mLeftComplication: ImageButton? = null
    private var mRightComplication: ImageButton? = null

    private var mDefaultAddComplicationDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_config)

        mDefaultAddComplicationDrawable = getDrawable(R.drawable.add_complication)

        // TODO: Step 3, initialize 1
        mSelectedComplicationId = -1

        mWatchFaceComponentName = ComponentName(applicationContext, DigitalWatchFaceService::class.java!!)

        // Sets up left complication preview.
        mLeftComplicationBackground = findViewById(R.id.left_complication_background)
        mLeftComplication = findViewById(R.id.left_complication)
        mLeftComplication?.setOnClickListener(this)

        // Sets default as "Add Complication" icon.
        mLeftComplication?.setImageDrawable(mDefaultAddComplicationDrawable)
        mLeftComplicationBackground?.visibility = View.INVISIBLE

        // Sets up right complication preview.
        mRightComplicationBackground = findViewById(R.id.right_complication_background)
        mRightComplication = findViewById(R.id.right_complication)
        mRightComplication?.setOnClickListener(this)

        // Sets default as "Add Complication" icon.
        mRightComplication?.setImageDrawable(mDefaultAddComplicationDrawable)
        mRightComplicationBackground?.visibility = View.INVISIBLE

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
        mProviderInfoRetriever!!.release()
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
        if (view == mLeftComplication) {
            Log.d(TAG, "Left Complication click()")
            launchComplicationHelperActivity(DigitalWatchFaceService.COMPLICATION_ID_TL)

        } else if (view == mRightComplication) {
            Log.d(TAG, "Right Complication click()")
            launchComplicationHelperActivity(DigitalWatchFaceService.COMPLICATION_ID_TR)
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

        if (watchFaceComplicationId == DigitalWatchFaceService.COMPLICATION_ID_TL) {
            if (complicationProviderInfo != null) {
                mLeftComplication!!.setImageIcon(complicationProviderInfo.providerIcon)
                mLeftComplicationBackground!!.visibility = View.VISIBLE

            } else {
                mLeftComplication!!.setImageDrawable(mDefaultAddComplicationDrawable)
                mLeftComplicationBackground!!.visibility = View.INVISIBLE
            }

        } else if (watchFaceComplicationId == DigitalWatchFaceService.COMPLICATION_ID_TR) {
            if (complicationProviderInfo != null) {
                mRightComplication!!.setImageIcon(complicationProviderInfo.providerIcon)
                mRightComplicationBackground!!.visibility = View.VISIBLE

            } else {
                mRightComplication!!.setImageDrawable(mDefaultAddComplicationDrawable)
                mRightComplicationBackground!!.visibility = View.INVISIBLE
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {

        // TODO: Step 3, update views
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            // Retrieves information for selected Complication provider.
            val complicationProviderInfo = data.getParcelableExtra<ComplicationProviderInfo>(ProviderChooserIntent.EXTRA_PROVIDER_INFO)
            Log.d(TAG, "Provider: " + complicationProviderInfo)

            if (mSelectedComplicationId >= 0) {
                updateComplicationViews(mSelectedComplicationId, complicationProviderInfo)
            }
        }
    }
}
