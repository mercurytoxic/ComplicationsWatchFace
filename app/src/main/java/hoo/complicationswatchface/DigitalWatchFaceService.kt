package hoo.complicationswatchface

import android.app.PendingIntent
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.util.SparseArray
import android.view.SurfaceHolder
import android.view.WindowInsets
import java.lang.ref.WeakReference
import java.util.*


/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class DigitalWatchFaceService : CanvasWatchFaceService() {

    companion object {
        private val TAG = "DigitalWatchFaceService"

        /**
         * Updates rate in milliseconds for interactive mode. We update once a second since seconds
         * are displayed in interactive mode.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0

        // TODO: Step 2, intro 1
        private const val COMPLICATION_ID_T = 0
        private const val COMPLICATION_ID_TR = 1
        private const val COMPLICATION_ID_BR = 2
        private const val COMPLICATION_ID_B = 3
        private const val COMPLICATION_ID_BL = 4
        private const val COMPLICATION_ID_TL = 5
        private const val COMPLICATION_ID_CNT = 6

        private val COMPLICATION_IDS =
                intArrayOf(
                        COMPLICATION_ID_T,
                        COMPLICATION_ID_TR,
                        COMPLICATION_ID_BR,
                        COMPLICATION_ID_B,
                        COMPLICATION_ID_BL,
                        COMPLICATION_ID_TL,
                        COMPLICATION_ID_CNT)

        // Left and right dial supported types.
        private val COMPLICATION_SUPPORTED_TYPES_NORMAL =
                intArrayOf(
                        ComplicationData.TYPE_RANGED_VALUE,
                        ComplicationData.TYPE_ICON,
                        ComplicationData.TYPE_SHORT_TEXT,
                        ComplicationData.TYPE_SMALL_IMAGE)

        private val COMPLICATION_SUPPORTED_TYPES_LONG =
                intArrayOf(
                        ComplicationData.TYPE_RANGED_VALUE,
                        ComplicationData.TYPE_ICON,
                        ComplicationData.TYPE_SHORT_TEXT,
                        ComplicationData.TYPE_SMALL_IMAGE,
                        ComplicationData.TYPE_LONG_TEXT)

        private val COMPLICATION_SUPPORTED_TYPES_TINY =
                intArrayOf(
                        ComplicationData.TYPE_ICON,
                        ComplicationData.TYPE_SMALL_IMAGE)

        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        // Used by {@link ComplicationConfigActivity} to retrieve all complication ids.
        // TODO: Step 3, expose complication information, part 2
        fun getComplicationIds(): IntArray {
            return COMPLICATION_IDS
        }

        // Used by {@link ComplicationConfigActivity} to retrieve complication types supported by
        // location.
        // TODO: Step 3, expose complication information, part 3
        fun getSupportedComplicationTypes(complicationLocation: Int): IntArray {
            // Add any other supported locations here.
            return when (complicationLocation) {
                //COMPLICATION_ID_T -> COMPLICATION_SUPPORTED_TYPES_LONG
                COMPLICATION_ID_CNT -> COMPLICATION_SUPPORTED_TYPES_TINY
                else -> COMPLICATION_SUPPORTED_TYPES_NORMAL
            }
        }
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: DigitalWatchFaceService.Engine) : Handler() {
        private val mWeakReference: WeakReference<DigitalWatchFaceService.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mTextPaint: Paint

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false

        // TODO: Step 2, intro 2
        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private var mActiveComplicationDataSparseArray: SparseArray<ComplicationData>? = null

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private var mComplicationDrawableSparseArray: SparseArray<ComplicationDrawable>? = null

        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@DigitalWatchFaceService)
                    .setAcceptsTapEvents(true)
                    .setShowUnreadCountIndicator(true)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build())

            mCalendar = Calendar.getInstance()

            // Initializes background.
            mBackgroundPaint = Paint().apply {
                color = ContextCompat.getColor(applicationContext, R.color.background)
            }

            initializeComplications()

            // Initializes Watch Face.
            mTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
            }
        }

        // TODO: Step 2, initializeComplications()
        private fun initializeComplications() {
            Log.d(TAG, "initializeComplications()")

            mComplicationDrawableSparseArray = SparseArray(COMPLICATION_IDS.size)
            mActiveComplicationDataSparseArray = SparseArray(COMPLICATION_IDS.size)

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face. In this watch face, we only create left and right,
            // but you could add many more.
            // All styles for the complications are defined in
            // drawable/custom_complication_styles.xml.
            var complicationId: Int

            for (i in COMPLICATION_IDS.indices) {
                complicationId = COMPLICATION_IDS[i]

                val style = when ( complicationId ) {
                    COMPLICATION_ID_CNT -> R.drawable.complication_style_count
                    else -> R.drawable.complication_style_with_border
                }

                val complicationDrawable = getDrawable(style) as ComplicationDrawable
                complicationDrawable.setContext(applicationContext)

                // Adds new complications to a SparseArray to simplify setting styles and ambient
                // properties for all complications, i.e., iterate over them all.
                mComplicationDrawableSparseArray!!.put(complicationId, complicationDrawable)
            }

//            setDefaultSystemComplicationProvider(
//                    COMPLICATION_ID_CNT,
//                    SystemProviders.UNREAD_NOTIFICATION_COUNT,
//                    ComplicationData.TYPE_ICON
//            )

            setActiveComplications(*COMPLICATION_IDS)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        // TODO: Step 2, onComplicationDataUpdate()
        override fun onComplicationDataUpdate(
                complicationId: Int, complicationData: ComplicationData?) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId)

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray!!.put(complicationId, complicationData)

            // Updates correct ComplicationDrawable with updated data.
            val complicationDrawable = mComplicationDrawableSparseArray!!.get(complicationId)
            complicationDrawable!!.setComplicationData(complicationData)

            invalidate()
        }

        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private fun getTappedComplicationId(x: Int, y: Int): Int {

            var complicationId: Int
            var complicationData: ComplicationData?
            var complicationDrawable: ComplicationDrawable

            val currentTimeMillis = System.currentTimeMillis()

            for (i in COMPLICATION_IDS.indices) {
                complicationId = COMPLICATION_IDS[i]
                complicationData = mActiveComplicationDataSparseArray!!.get(complicationId)

                if (complicationData != null
                        && complicationData.isActive(currentTimeMillis)
                        && complicationData.type != ComplicationData.TYPE_NOT_CONFIGURED
                        && complicationData.type != ComplicationData.TYPE_EMPTY) {

                    complicationDrawable = mComplicationDrawableSparseArray!!.get(complicationId)
                    val complicationBoundingRect = complicationDrawable.bounds

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationId
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.")
                    }
                }
            }
            return -1
        }

        // Fires PendingIntent associated with complication (if it has one).
        private fun onComplicationTap(complicationId: Int) {
            // TODO: Step 5, onComplicationTap()
            Log.d(TAG, "onComplicationTap()")

            val complicationDrawable = mComplicationDrawableSparseArray!!.get(complicationId)
            val complicationData = mActiveComplicationDataSparseArray!!.get(complicationId)

            if (complicationData != null) {
                complicationData.value
                complicationDrawable?.setIsHighlighted(true)

                Thread(Runnable {
                    try {
                        Thread.sleep(150)
                    } catch (ie: InterruptedException) {
                    }
                    complicationDrawable?.setIsHighlighted(false)
                }).start()

                if (complicationData.tapAction != null) {
                    try {
                        complicationData.tapAction.send()
                    } catch (e: PendingIntent.CanceledException) {
                        Log.e(TAG, "onComplicationTap() tap action error: " + e)
                    }

                } else if (complicationData.type == ComplicationData.TYPE_NO_PERMISSION) {

                    // Watch face does not have permission to receive complication data, so launch
                    // permission request.
                    val componentName = ComponentName(
                            applicationContext, DigitalWatchFaceService::class.java)

                    val permissionRequestIntent = ComplicationHelperActivity.createPermissionRequestHelperIntent(
                            applicationContext, componentName)

                    startActivity(permissionRequestIntent)
                }

            } else {
                Log.d(TAG, "No PendingIntent for complication $complicationId.")
            }
        }
        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mTextPaint.isAntiAlias = !inAmbientMode
            }

            // TODO: Step 2, ambient
            var complicationDrawable: ComplicationDrawable

            for (i in COMPLICATION_IDS.indices) {
                complicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_IDS[i])
                complicationDrawable.setInAmbientMode(mAmbient)
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    val tappedComplicationId = getTappedComplicationId(x, y)
                    if (tappedComplicationId != -1) {
                        onComplicationTap(tappedComplicationId)
                    }
                }
            }

            invalidate()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            /*
             * Calculates location bounds for right and left circular complications. Please note,
             * we are not demonstrating a long text complication in this watch face.
             *
             * We suggest using at least 1/4 of the screen width for circular (or squared)
             * complications and 2/3 of the screen width for wide rectangular complications for
             * better readability.
             */

            // For most Wear devices, width and height are the same, so we just chose one (width).

            Log.d(TAG, "onSurfaceChanged")

            // TODO: Step 2, calculating ComplicationDrawable locations
            val sizeOfComplication = (width * 0.25).toInt()
            val tinySizeOfComplication = (width * 0.1).toInt()
            val midpointOfScreen = width / 2

            val topBottomOffset = (width * 0.04).toInt()
            val diagonalHOffset = (width * 0.1).toInt()
            val diagonalVOffset = (width * 0.2).toInt()

            Log.d(TAG, "onSurfaceChanged" + sizeOfComplication + midpointOfScreen)

            val tlBounds =
                    // Left, Top, Right, Bottom
                    Rect(   diagonalHOffset,
                            diagonalVOffset,
                            diagonalHOffset + sizeOfComplication,
                            diagonalVOffset + sizeOfComplication)

            val tlComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_TL)
            tlComplicationDrawable!!.bounds = tlBounds

            val trBounds =
                    // Left, Top, Right, Bottom
                    Rect(   width - diagonalHOffset - sizeOfComplication,
                            diagonalVOffset,
                            width - diagonalHOffset,
                            diagonalVOffset + sizeOfComplication)

            val trComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_TR)
            trComplicationDrawable!!.bounds = trBounds

            val blBounds =
                    // Left, Top, Right, Bottom
                    Rect(   diagonalHOffset,
                            height - diagonalVOffset - sizeOfComplication,
                            diagonalHOffset + sizeOfComplication,
                            height - diagonalVOffset)

            val blComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_BL)
            blComplicationDrawable!!.bounds = blBounds

            val brBounds =
                    // Left, Top, Right, Bottom
                    Rect(   width - diagonalHOffset - sizeOfComplication,
                            height - diagonalVOffset - sizeOfComplication,
                            width - diagonalHOffset,
                            height - diagonalVOffset)

            val brComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_BR)
            brComplicationDrawable!!.bounds = brBounds

            val tBounds =
                    // Left, Top, Right, Bottom
                    Rect(   midpointOfScreen - sizeOfComplication / 2,
                            topBottomOffset,
                            midpointOfScreen + sizeOfComplication / 2,
                            topBottomOffset + sizeOfComplication)

            val topComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_T)
            topComplicationDrawable!!.bounds = tBounds

            val bBounds =
                    // Left, Top, Right, Bottom
                    Rect(   midpointOfScreen - sizeOfComplication / 2,
                            height - topBottomOffset - sizeOfComplication,
                            midpointOfScreen + sizeOfComplication / 2,
                            height - topBottomOffset)

            val bottomComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_B)
            bottomComplicationDrawable!!.bounds = bBounds

            val tinyBounds =
                    // Left, Top, Right, Bottom
                    Rect(   midpointOfScreen - tinySizeOfComplication / 2,
                            height - topBottomOffset * 2 - sizeOfComplication - tinySizeOfComplication,
                            midpointOfScreen + tinySizeOfComplication / 2,
                            height - topBottomOffset * 2 - sizeOfComplication)

            val tinyComplicationDrawable = mComplicationDrawableSparseArray!!.get(COMPLICATION_ID_CNT)
            tinyComplicationDrawable!!.bounds = tinyBounds
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawComplications(canvas, now)
            drawWatchFace(canvas, bounds)
        }

        private fun drawBackground(canvas: Canvas) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawPaint(mBackgroundPaint)
                /*
                canvas.drawRect(
                        0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), mBackgroundPaint)
                */
            }
        }

        private fun drawComplications(canvas: Canvas, currentTimeMillis: Long) {
            // TODO: Step 4, drawComplications()
            var complicationId: Int
            var complicationDrawable: ComplicationDrawable

            Log.d(TAG, "drawComplications()")

            for (i in COMPLICATION_IDS.indices) {
                complicationId = COMPLICATION_IDS[i]
                complicationDrawable = mComplicationDrawableSparseArray!!.get(complicationId)

                complicationDrawable.draw(canvas, currentTimeMillis)
            }
        }

        private fun drawWatchFace(canvas: Canvas, bounds: Rect) {
            // Draw HH:MM in ambient mode or HH:MM:SS in interactive mode.
            Log.d(TAG, "drawWatchFace()")

            val text = if (mAmbient)
                String.format("%02d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                        mCalendar.get(Calendar.MINUTE))
            else
                String.format("%02d:%02d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                        mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND))

            // Get the Offset of middle text
            val textBounds = Rect()
            mTextPaint.getTextBounds(text, 0, text.length, textBounds)
            val xOffset = Math.abs(bounds.centerX() - textBounds.centerX())
            val yOffset = Math.abs(bounds.centerY() - textBounds.centerY())

            canvas.drawText(text, xOffset.toFloat(), yOffset.toFloat(), mTextPaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@DigitalWatchFaceService.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@DigitalWatchFaceService.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@DigitalWatchFaceService.resources
            val textSize = resources.getDimension(R.dimen.time_text_size)

            mTextPaint.textSize = textSize
        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
