package com.castellon.yayosgame.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * YAYOS GAME - Level 1: Barcelona - La Sagrada Familia
 *
 * Retro 70s/80s pixel style using pure chunky Canvas drawing.
 * Mix of Frogger crossing + Lemmings-style character management.
 */
class YayoGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    companion object {
        // Virtual resolution (chunky retro feel)
        const val VW = 360
        const val VH = 640

        // Road layout (top to bottom)
        const val GOAL_TOP = 0
        const val GOAL_BOTTOM = 92
        const val ROAD3_TOP = 92
        const val ROAD3_BOTTOM = 158
        const val SAFE2_TOP = 158
        const val SAFE2_BOTTOM = 190
        const val ROAD2_TOP = 190
        const val ROAD2_BOTTOM = 256
        const val SAFE1_TOP = 256
        const val SAFE1_BOTTOM = 288
        const val ROAD1_TOP = 288
        const val ROAD1_BOTTOM = 354
        const val BOTTOM_SAFE_TOP = 354

        // Vertical streets
        const val VERT_LEFT_LEFT = 68
        const val VERT_LEFT_RIGHT = 108
        const val VERT_RIGHT_LEFT = 252
        const val VERT_RIGHT_RIGHT = 292

        // Colors (retro palette)
        private val DARK_BLUE = 0xFF1A1A4A.toInt()
        private val ROAD = 0xFF222233.toInt()
        private val SIDEWALK = 0xFFC4A77D.toInt()
        private val YELLOW = 0xFFF4E04D.toInt()
        private val ORANGE = 0xFFE07020.toInt()
        private val RED = 0xFFB03030.toInt()
        private val GREEN = 0xFF2E8B57.toInt()
        private val WHITE = 0xFFF5F5F5.toInt()
        private val LIGHT_BLUE = 0xFF5DADE2.toInt()
        private val PINK = 0xFFE070A0.toInt()
        private val GRAY = 0xFF666677.toInt()
        private val BLACK = 0xFF111111.toInt()
    }

    private var gameThread: GameThread? = null
    private val paint = Paint().apply {
        isAntiAlias = false
        isDither = false
    }

    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Game state
    private var state: GameState = GameState.MENU
    private var difficulty: Difficulty = Difficulty.MEDIUM
    private var frame = 0L

    // Entities
    private val yayos = mutableListOf<Yayo>()
    private val vehicles = mutableListOf<Vehicle>()
    private val lights = mutableListOf<TrafficLight>()

    private var bus: Bus? = null
    private var selectedYayoId: Int = -1

    // Score
    private var savedCount = 0
    private var deadCount = 0

    // Timing
    private var lastTime = System.currentTimeMillis()
    private var gameTime = 0L
    private val MAX_GAME_TIME = 110_000L // ~1:50 minutes

    // Command buttons (retro chunky)
    private val btnStop = RectF(30f, VH - 68f, 115f, VH - 18f)
    private val btnGo = RectF(130f, VH - 68f, 215f, VH - 18f)
    private val btnHurry = RectF(230f, VH - 68f, 330f, VH - 18f)

    // Difficulty buttons
    private val btnEasy = RectF(40f, 280f, 140f, 340f)
    private val btnMedium = RectF(160f, 280f, 260f, 340f)
    private val btnHard = RectF(280f, 280f, 320f, 340f)

    init {
        holder.addCallback(this)
        isFocusable = true
        setupTrafficLights()
    }

    private fun setupTrafficLights() {
        lights.clear()
        // Four lights controlling different groups (short cycles as requested: 6-8s)
        lights.add(TrafficLight(85f, 120f, 7200L, 0L))   // controls road3 left vertical
        lights.add(TrafficLight(270f, 120f, 6800L, 1800L)) // road3 right
        lights.add(TrafficLight(85f, 310f, 7500L, 900L))   // road1 left
        lights.add(TrafficLight(270f, 310f, 7100L, 2200L)) // road1 right
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startGameThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val gameAspect = VW.toFloat() / VH
        val viewAspect = width.toFloat() / height

        if (viewAspect > gameAspect) {
            scaleY = height.toFloat() / VH
            scaleX = scaleY
            offsetX = (width - VW * scaleX) / 2f
            offsetY = 0f
        } else {
            scaleX = width.toFloat() / VW
            scaleY = scaleX
            offsetX = 0f
            offsetY = (height - VH * scaleY) / 2f
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        shutdown()
    }

    private fun startGameThread() {
        if (gameThread?.isRunning == true) return
        gameThread = GameThread()
        gameThread?.start()
    }

    fun pause() {
        gameThread?.pauseThread()
    }

    fun resume() {
        gameThread?.resumeThread()
    }

    fun shutdown() {
        gameThread?.shutdown()
        gameThread = null
    }

    // ==================== INPUT ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        val vx = (event.x - offsetX) / scaleX
        val vy = (event.y - offsetY) / scaleY

        when (state) {
            GameState.MENU -> handleMenuTouch(vx, vy)
            GameState.PLAYING -> handlePlayingTouch(vx, vy)
            GameState.GAME_OVER -> handleGameOverTouch(vx, vy)
        }
        return true
    }

    private fun handleMenuTouch(vx: Float, vy: Float) {
        when {
            btnEasy.contains(vx, vy) -> startGame(Difficulty.EASY)
            btnMedium.contains(vx, vy) -> startGame(Difficulty.MEDIUM)
            btnHard.contains(vx, vy) -> startGame(Difficulty.HARD)
        }
    }

    private fun handlePlayingTouch(vx: Float, vy: Float) {
        // Command buttons
        if (selectedYayoId != -1) {
            when {
                btnStop.contains(vx, vy) -> {
                    yayos.find { it.id == selectedYayoId }?.let { it.forceStopUntil = frame + 180 }
                }
                btnGo.contains(vx, vy) -> {
                    yayos.find { it.id == selectedYayoId }?.let { it.forceGoUntil = frame + 90 }
                }
                btnHurry.contains(vx, vy) -> {
                    yayos.find { it.id == selectedYayoId }?.let { it.hurryUntil = frame + 120 }
                }
            }
        }

        // Select nearest yayo
        var closest: Yayo? = null
        var minDist = 38f
        for (y in yayos) {
            if (y.state != YayoState.ALIVE) continue
            val d = abs(y.x - vx) + abs(y.y - vy)
            if (d < minDist) {
                minDist = d
                closest = y
            }
        }
        selectedYayoId = closest?.id ?: -1
    }

    private fun handleGameOverTouch(vx: Float, vy: Float) {
        // Big retry area
        if (vy > 380 && vy < 460) {
            state = GameState.MENU
            selectedYayoId = -1
        }
    }

    // ==================== GAME LOOP ====================

    private inner class GameThread : Thread() {
        @Volatile var isRunning = true
        @Volatile var isPaused = false

        override fun run() {
            var lastFrame = System.currentTimeMillis()

            while (isRunning) {
                if (isPaused) {
                    sleep(50)
                    continue
                }

                val now = System.currentTimeMillis()
                val delta = now - lastFrame
                if (delta < 16) {
                    sleep(4)
                    continue
                }
                lastFrame = now

                update()
                drawFrame()
                frame++
            }
        }

        fun pauseThread() { isPaused = true }
        fun resumeThread() { isPaused = false; lastFrame = System.currentTimeMillis() }
        fun shutdown() { isRunning = false }
    }

    private fun update() {
        if (state != GameState.PLAYING) return

        gameTime += 16
        if (gameTime > MAX_GAME_TIME) {
            endGame()
            return
        }

        // Update lights
        lights.forEach { it.update(frame) }

        // Update bus arrival
        bus?.let { b ->
            b.update()
            if (b.state == BusState.UNLOADING && b.spawnedYayos < 6 && frame % 28 == 0L) {
                spawnNextYayo(b.spawnedYayos)
                b.spawnedYayos++
                if (b.spawnedYayos >= 6) b.state = BusState.DONE
            }
        }

        // Update yayos
        val toRemove = mutableListOf<Yayo>()
        for (y in yayos) {
            y.update(frame, this::isOnRoad, this::isInGoalZone)

            // Check collision with vehicles
            if (y.state == YayoState.ALIVE && isOnRoad(y.y)) {
                for (v in vehicles) {
                    if (v.collidesWith(y)) {
                        y.state = YayoState.DEAD
                        deadCount++
                        selectedYayoId = if (selectedYayoId == y.id) -1 else selectedYayoId
                        break
                    }
                }
            }

            // Saved
            if (y.state == YayoState.ALIVE && isInGoalZone(y.x, y.y)) {
                y.state = YayoState.SAVED
                savedCount++
                selectedYayoId = if (selectedYayoId == y.id) -1 else selectedYayoId
            }

            if (y.state == YayoState.DEAD || y.state == YayoState.SAVED) {
                if (frame - y.deathOrSaveFrame > 90) toRemove.add(y)
            }
        }
        yayos.removeAll(toRemove)

        // Update vehicles
        vehicles.forEach { it.update(VW.toFloat()) }

        // Spawn more vehicles based on difficulty
        spawnVehiclesIfNeeded()

        // Check win/lose
        val active = yayos.count { it.state == YayoState.ALIVE }
        if (active == 0 && (bus == null || bus?.state == BusState.DONE)) {
            if (yayos.isEmpty() || yayos.all { it.state != YayoState.ALIVE }) {
                endGame()
            }
        }
    }

    private fun isOnRoad(y: Float): Boolean =
        (y in ROAD1_TOP..ROAD1_BOTTOM) || (y in ROAD2_TOP..ROAD2_BOTTOM) || (y in ROAD3_TOP..ROAD3_BOTTOM)

    private fun isInGoalZone(x: Float, y: Float): Boolean =
        y < GOAL_BOTTOM + 12 && x > 110 && x < 250

    private fun spawnNextYayo(index: Int) {
        val baseY = 480f + Random.nextFloat() * 30f
        val baseX = 120f + (index % 3) * 45f + Random.nextFloat() * 12f

        val (speed, tiredness, color, name) = when (index) {
            0 -> Triple(0.92f, 0.018f, 0xFFE8B070.toInt(), "BASTON")
            1 -> Triple(0.68f, 0.022f, 0xFF70A0E0.toInt(), "ANDADOR")
            2 -> Triple(0.52f, 0.009f, 0xFF90C070.toInt(), "SILLA")
            3 -> Triple(0.81f, 0.015f, 0xFFE070A0.toInt(), "TACATACA")
            4 -> Triple(1.38f, 0.031f, 0xFFF0D070.toInt(), "RAPIDA")
            else -> Triple(1.08f, 0.014f, 0xFF80B0D0.toInt(), "DOBLE")
        }

        val adjustedSpeed = when (difficulty) {
            Difficulty.EASY -> speed * 1.1f
            Difficulty.HARD -> speed * 0.92f
            else -> speed
        }

        yayos.add(
            Yayo(
                id = 100 + index,
                x = baseX,
                y = baseY,
                baseSpeed = adjustedSpeed,
                tirednessChance = tiredness,
                color = color,
                name = name
            )
        )
    }

    private fun spawnVehiclesIfNeeded() {
        val maxVehicles = when (difficulty) {
            Difficulty.EASY -> 6
            Difficulty.MEDIUM -> 10
            Difficulty.HARD -> 15
        }

        if (vehicles.size >= maxVehicles) return

        // Horizontal roads - left to right and right to left
        val spawnChance = when (difficulty) {
            Difficulty.EASY -> 0.018f
            Difficulty.MEDIUM -> 0.031f
            Difficulty.HARD -> 0.044f
        }

        if (Random.nextFloat() < spawnChance) spawnVehicleOnHorizontal(ROAD1_TOP + 18f, 1f)
        if (Random.nextFloat() < spawnChance * 0.9f) spawnVehicleOnHorizontal(ROAD1_TOP + 48f, -1f)
        if (Random.nextFloat() < spawnChance) spawnVehicleOnHorizontal(ROAD2_TOP + 22f, 1f)
        if (Random.nextFloat() < spawnChance * 0.85f) spawnVehicleOnHorizontal(ROAD2_TOP + 46f, -1f)
        if (Random.nextFloat() < spawnChance * 0.95f) spawnVehicleOnHorizontal(ROAD3_TOP + 20f, -1f)
        if (Random.nextFloat() < spawnChance) spawnVehicleOnHorizontal(ROAD3_TOP + 44f, 1f)

        // Vertical traffic (north-south)
        if (Random.nextFloat() < spawnChance * 0.7f) spawnVehicleOnVertical(VERT_LEFT_LEFT + 12f, 1f)
        if (Random.nextFloat() < spawnChance * 0.65f) spawnVehicleOnVertical(VERT_RIGHT_LEFT + 14f, -1f)
    }

    private fun spawnVehicleOnHorizontal(y: Float, dir: Float) {
        val type = VehicleType.values().random()
        val speed = when (type) {
            VehicleType.PATINETE -> 4.6f
            VehicleType.MOTO -> 3.7f
            VehicleType.COCHE -> 2.45f
            VehicleType.AUTOBUS -> 1.65f
        }
        val startX = if (dir > 0) -60f else VW + 60f
        vehicles.add(Vehicle(startX, y, dir, speed, type))
    }

    private fun spawnVehicleOnVertical(x: Float, dir: Float) {
        val type = if (Random.nextFloat() < 0.25f) VehicleType.MOTO else VehicleType.COCHE
        val speed = if (type == VehicleType.MOTO) 3.9f else 2.3f
        val startY = if (dir > 0) -70f else VH + 70f
        vehicles.add(Vehicle(x, startY, dir, speed, type, isVertical = true))
    }

    private fun startGame(diff: Difficulty) {
        difficulty = diff
        state = GameState.PLAYING
        frame = 0
        gameTime = 0
        savedCount = 0
        deadCount = 0
        selectedYayoId = -1
        yayos.clear()
        vehicles.clear()

        // Bus arrives from left
        bus = Bus(-140f, 470f)

        // Initial vehicles (a few already on the roads)
        repeat(when (diff) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 5
            Difficulty.HARD -> 7
        }) {
            spawnVehicleOnHorizontal(ROAD1_TOP + 18f + Random.nextFloat()*28f, if (Random.nextBoolean()) 1f else -1f)
            spawnVehicleOnHorizontal(ROAD2_TOP + 22f + Random.nextFloat()*20f, if (Random.nextBoolean()) 1f else -1f)
        }

        // A couple of vertical ones
        spawnVehicleOnVertical(VERT_LEFT_LEFT + 12f, 1f)
        spawnVehicleOnVertical(VERT_RIGHT_LEFT + 14f, -1f)
    }

    private fun endGame() {
        state = GameState.GAME_OVER
        bus = null
    }

    // ==================== DRAWING (pure retro pixel style) ====================

    private fun drawFrame() {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.save()
            canvas.translate(offsetX, offsetY)
            canvas.scale(scaleX, scaleY)

            // Background
            canvas.drawColor(DARK_BLUE)

            when (state) {
                GameState.MENU -> drawMenu(canvas)
                GameState.PLAYING -> {
                    drawMap(canvas)
                    drawVehicles(canvas)
                    drawYayos(canvas)
                    drawBus(canvas)
                    drawLights(canvas)
                    drawUI(canvas)
                }
                GameState.GAME_OVER -> drawGameOver(canvas)
            }

            canvas.restore()
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMenu(canvas: Canvas) {
        // Title
        paint.color = YELLOW
        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.drawText("YAYOS", 92f, 110f, paint)
        paint.textSize = 26f
        canvas.drawText("GAME", 128f, 145f, paint)

        paint.color = WHITE
        paint.textSize = 15f
        paint.isFakeBoldText = false
        canvas.drawText("BARCELONA 1984 STYLE", 98f, 178f, paint)

        // Subtitle
        paint.color = LIGHT_BLUE
        paint.textSize = 13f
        canvas.drawText("FROGGER + LEMMINGS + ABUELOS", 68f, 215f, paint)

        // Difficulty
        paint.color = WHITE
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("CHOOSE DIFFICULTY", 88f, 262f, paint)

        // Buttons
        drawRetroButton(canvas, btnEasy, "EASY", GREEN)
        drawRetroButton(canvas, btnMedium, "MEDIUM", ORANGE)
        drawRetroButton(canvas, btnHard, "HARD", RED)

        paint.color = GRAY
        paint.textSize = 11f
        canvas.drawText("Tap a button to start", 110f, 390f, paint)
        canvas.drawText("Get as many yayos as possible to Sagrada Familia", 38f, 420f, paint)
    }

    private fun drawRetroButton(canvas: Canvas, rect: RectF, text: String, color: Int) {
        paint.color = color
        canvas.drawRect(rect, paint)
        paint.color = BLACK
        canvas.drawRect(rect.left + 3, rect.top + 3, rect.right - 3, rect.bottom - 3, paint)
        paint.color = WHITE
        paint.textSize = 16f
        paint.isFakeBoldText = true
        val tw = paint.measureText(text)
        canvas.drawText(text, rect.centerX() - tw / 2, rect.centerY() + 6, paint)
    }

    private fun drawMap(canvas: Canvas) {
        // Sidewalks
        paint.color = SIDEWALK
        canvas.drawRect(0f, SAFE1_TOP.toFloat(), VW.toFloat(), SAFE1_BOTTOM.toFloat(), paint)
        canvas.drawRect(0f, SAFE2_TOP.toFloat(), VW.toFloat(), SAFE2_BOTTOM.toFloat(), paint)
        canvas.drawRect(0f, BOTTOM_SAFE_TOP.toFloat(), VW.toFloat(), VH.toFloat(), paint)

        // Roads
        paint.color = ROAD
        canvas.drawRect(0f, ROAD1_TOP.toFloat(), VW.toFloat(), ROAD1_BOTTOM.toFloat(), paint)
        canvas.drawRect(0f, ROAD2_TOP.toFloat(), VW.toFloat(), ROAD2_BOTTOM.toFloat(), paint)
        canvas.drawRect(0f, ROAD3_TOP.toFloat(), VW.toFloat(), ROAD3_BOTTOM.toFloat(), paint)

        // Vertical streets
        canvas.drawRect(VERT_LEFT_LEFT.toFloat(), 0f, VERT_LEFT_RIGHT.toFloat(), VH.toFloat(), paint)
        canvas.drawRect(VERT_RIGHT_LEFT.toFloat(), 0f, VERT_RIGHT_RIGHT.toFloat(), VH.toFloat(), paint)

        // Lane markings (chunky dashed)
        paint.color = YELLOW
        paint.strokeWidth = 2f
        // Horizontal roads
        for (roadTop in listOf(ROAD1_TOP, ROAD2_TOP, ROAD3_TOP)) {
            val center = roadTop + 33f
            for (x in 0..VW step 28) {
                canvas.drawLine(x.toFloat(), center, x + 16f, center, paint)
            }
        }
        // Vertical
        for (vLeft in listOf(VERT_LEFT_LEFT, VERT_RIGHT_LEFT)) {
            val center = vLeft + 20f
            for (y in 0..VH step 26) {
                canvas.drawLine(center, y.toFloat(), center, y + 15f, paint)
            }
        }

        // Thick zebra crossings at all intersections
        paint.color = WHITE
        val zebraWidth = 18f
        val zebraLines = 5
        // Road 1 zebras
        drawZebra(canvas, VERT_LEFT_LEFT, ROAD1_TOP, VERT_LEFT_RIGHT - VERT_LEFT_LEFT, ROAD1_BOTTOM - ROAD1_TOP, true)
        drawZebra(canvas, VERT_RIGHT_LEFT, ROAD1_TOP, VERT_RIGHT_RIGHT - VERT_RIGHT_LEFT, ROAD1_BOTTOM - ROAD1_TOP, true)
        // Road 2
        drawZebra(canvas, VERT_LEFT_LEFT, ROAD2_TOP, VERT_LEFT_RIGHT - VERT_LEFT_LEFT, ROAD2_BOTTOM - ROAD2_TOP, true)
        drawZebra(canvas, VERT_RIGHT_LEFT, ROAD2_TOP, VERT_RIGHT_RIGHT - VERT_RIGHT_LEFT, ROAD2_BOTTOM - ROAD2_TOP, true)
        // Road 3
        drawZebra(canvas, VERT_LEFT_LEFT, ROAD3_TOP, VERT_LEFT_RIGHT - VERT_LEFT_LEFT, ROAD3_BOTTOM - ROAD3_TOP, true)
        drawZebra(canvas, VERT_RIGHT_LEFT, ROAD3_TOP, VERT_RIGHT_RIGHT - VERT_RIGHT_LEFT, ROAD3_BOTTOM - ROAD3_TOP, true)

        // Goal area (Sagrada Familia)
        paint.color = 0xFF3A3A5A.toInt()
        canvas.drawRect(0f, GOAL_TOP.toFloat(), VW.toFloat(), GOAL_BOTTOM.toFloat(), paint)

        // Sagrada Familia silhouette (very recognizable even at low res)
        drawSagradaFamilia(canvas)

        // Street labels
        paint.color = 0xFFAAAAAA.toInt()
        paint.textSize = 9f
        canvas.drawText("AV. GAUDI", 8f, 50f, paint)
        canvas.drawText("C/ SARDENYA", 260f, 50f, paint)
    }

    private fun drawZebra(canvas: Canvas, x: Int, y: Int, w: Int, h: Int, horizontal: Boolean) {
        paint.color = WHITE
        val step = if (horizontal) 7 else 6
        val thick = if (horizontal) 3 else 4
        if (horizontal) {
            val cy = y + h / 2f
            for (i in 0 until w step step * 2) {
                canvas.drawRect(x + i.toFloat(), cy - thick, x + i + step.toFloat(), cy + thick, paint)
            }
        } else {
            val cx = x + w / 2f
            for (i in 0 until h step step * 2) {
                canvas.drawRect(cx - thick, y + i.toFloat(), cx + thick, y + i + step.toFloat(), paint)
            }
        }
    }

    private fun drawSagradaFamilia(canvas: Canvas) {
        paint.color = 0xFFE8E0C8.toInt()
        val cx = 180f
        val base = 78f

        // Central spire (very tall and pointy)
        val path = android.graphics.Path()
        path.moveTo(cx, 8f)
        path.lineTo(cx - 11f, base)
        path.lineTo(cx + 11f, base)
        path.close()
        canvas.drawPath(path, paint)

        // Four towers
        canvas.drawRect(cx - 38, 32f, cx - 26, base, paint)
        canvas.drawRect(cx - 20, 26f, cx - 8, base, paint)
        canvas.drawRect(cx + 8, 26f, cx + 20, base, paint)
        canvas.drawRect(cx + 26, 32f, cx + 38, base, paint)

        // Little crosses on towers
        paint.color = WHITE
        canvas.drawRect(cx - 34, 28f, cx - 30, 32f, paint)
        canvas.drawRect(cx - 35, 25f, cx - 29, 29f, paint)
        // etc for others (simplified)
    }

    private fun drawBus(canvas: Canvas) {
        val b = bus ?: return
        if (b.state == BusState.GONE) return

        paint.color = YELLOW
        val x = b.x
        val y = b.y
        val w = 92f
        val h = 38f

        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.color = BLACK
        canvas.drawRect(x + 4, y + 6, x + w - 4, y + h - 6, paint) // windows

        // "TMB" text
        paint.color = 0xFF222222.toInt()
        paint.textSize = 11f
        canvas.drawText("TMB", x + 28, y + 24, paint)

        // Wheels
        paint.color = BLACK
        canvas.drawRect(x + 12, y + h - 6, x + 24, y + h + 2, paint)
        canvas.drawRect(x + 66, y + h - 6, x + 78, y + h + 2, paint)

        // Doors open animation
        if (b.state == BusState.UNLOADING) {
            paint.color = 0xFF333333.toInt()
            canvas.drawRect(x + 38, y + 8, x + 48, y + h - 8, paint)
        }
    }

    private fun drawVehicles(canvas: Canvas) {
        for (v in vehicles) {
            val color = when (v.type) {
                VehicleType.PATINETE -> 0xFF40C0FF.toInt()
                VehicleType.MOTO -> 0xFF505050.toInt()
                VehicleType.COCHE -> 0xFF8B0000.toInt()
                VehicleType.AUTOBUS -> 0xFF2E5A2E.toInt()
            }
            paint.color = color

            if (!v.isVertical) {
                // Horizontal vehicle
                val w = when (v.type) {
                    VehicleType.PATINETE -> 18f
                    VehicleType.MOTO -> 26f
                    VehicleType.COCHE -> 38f
                    VehicleType.AUTOBUS -> 64f
                }
                val h = if (v.type == VehicleType.AUTOBUS) 22f else 16f
                canvas.drawRect(v.x, v.y - h/2, v.x + w, v.y + h/2, paint)

                // Windows / details
                paint.color = 0xFF88FFEE.toInt()
                if (v.type == VehicleType.AUTOBUS) {
                    canvas.drawRect(v.x + 8, v.y - 7, v.x + w - 8, v.y + 7, paint)
                } else if (v.type != VehicleType.PATINETE) {
                    canvas.drawRect(v.x + 6, v.y - 4, v.x + w - 6, v.y + 4, paint)
                }
            } else {
                // Vertical
                val w = 15f
                val h = when (v.type) {
                    VehicleType.MOTO -> 24f
                    else -> 34f
                }
                canvas.drawRect(v.x - w/2, v.y, v.x + w/2, v.y + h, paint)
            }
        }
    }

    private fun drawYayos(canvas: Canvas) {
        for (y in yayos) {
            if (y.state == YayoState.DEAD) {
                // X marks the spot
                paint.color = RED
                canvas.drawLine(y.x - 7, y.y - 7, y.x + 7, y.y + 7, paint)
                canvas.drawLine(y.x + 7, y.y - 7, y.x - 7, y.y + 7, paint)
                continue
            }
            if (y.state == YayoState.SAVED) continue

            val isTired = y.isTired(frame)
            val isHurry = y.hurryUntil > frame
            val speedMul = if (isHurry) 1.9f else 1f

            // Body
            paint.color = y.color
            val bw = if (y.name == "SILLA") 13f else 9f
            canvas.drawRect(y.x - bw, y.y - 6, y.x + bw, y.y + 5, paint)

            // Head
            paint.color = 0xFFFFE0C0.toInt()
            canvas.drawRect(y.x - 4, y.y - 13, y.x + 4, y.y - 6, paint)

            // Cane / Walker / Wheelchair indicators
            paint.color = 0xFF333333.toInt()
            when (y.name) {
                "BASTON" -> {
                    canvas.drawLine(y.x + 6, y.y - 2, y.x + 11, y.y + 12, paint)
                    canvas.drawLine(y.x + 8, y.y + 10, y.x + 13, y.y + 10, paint)
                }
                "ANDADOR" -> {
                    canvas.drawRect(y.x - 3, y.y + 5, y.x + 3, y.y + 11, paint)
                }
                "SILLA" -> {
                    canvas.drawCircle(y.x - 8, y.y + 8, 3f, paint)
                    canvas.drawCircle(y.x + 8, y.y + 8, 3f, paint)
                }
                "TACATACA" -> {
                    canvas.drawRect(y.x - 2, y.y + 5, y.x + 10, y.y + 9, paint)
                }
            }

            // Tired indicator
            if (isTired) {
                paint.color = WHITE
                paint.textSize = 8f
                canvas.drawText("z", y.x + 7, y.y - 14, paint)
            }

            // Selection ring
            if (y.id == selectedYayoId) {
                paint.color = YELLOW
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawCircle(y.x, y.y - 2, 18f, paint)
                paint.style = Paint.Style.FILL
                paint.strokeWidth = 1f

                // Name tag
                paint.color = BLACK
                paint.textSize = 7f
                val tw = paint.measureText(y.name)
                canvas.drawRect(y.x - tw/2 - 2, y.y - 26, y.x + tw/2 + 2, y.y - 18, paint)
                paint.color = YELLOW
                canvas.drawText(y.name, y.x - tw/2, y.y - 20, paint)
            }
        }
    }

    private fun drawLights(canvas: Canvas) {
        for (light in lights) {
            val isRed = light.isRed(frame)

            // Housing
            paint.color = 0xFF222222.toInt()
            canvas.drawRect(light.x - 6, light.y - 14, light.x + 6, light.y + 14, paint)

            // Lights
            paint.color = if (isRed) RED else 0xFF222222.toInt()
            canvas.drawCircle(light.x, light.y - 7, 3.5f, paint)

            paint.color = if (!isRed) GREEN else 0xFF222222.toInt()
            canvas.drawCircle(light.x, light.y + 7, 3.5f, paint)
        }
    }

    private fun drawUI(canvas: Canvas) {
        // Bottom bar
        paint.color = 0xFF0D0D22.toInt()
        canvas.drawRect(0f, VH - 78f, VW.toFloat(), VH.toFloat(), paint)

        // Score
        paint.color = YELLOW
        paint.textSize = 12f
        canvas.drawText("SAVED: $savedCount   LOST: $deadCount", 18f, VH - 82f, paint)

        // Timer
        val remaining = max(0, (MAX_GAME_TIME - gameTime) / 1000)
        canvas.drawText("${remaining}s", 290f, VH - 82f, paint)

        // Command buttons (only when something selected)
        if (selectedYayoId != -1) {
            drawRetroButton(canvas, btnStop, "STOP", 0xFF4A6A9A.toInt())
            drawRetroButton(canvas, btnGo, "GO", GREEN)
            drawRetroButton(canvas, btnHurry, "HURRY", ORANGE)
        } else {
            paint.color = GRAY
            paint.textSize = 10f
            canvas.drawText("TAP A YAYO TO COMMAND", 82f, VH - 42f, paint)
        }

        // Tiny Sagrada label
        paint.color = 0xFFAAAAAA.toInt()
        paint.textSize = 8f
        canvas.drawText("SAGRADA FAMILIA", 122f, 16f, paint)
    }

    private fun drawGameOver(canvas: Canvas) {
        paint.color = 0xFF0A0A2A.toInt()
        canvas.drawRect(0f, 140f, VW.toFloat(), 520f, paint)

        paint.color = if (savedCount >= 4) GREEN else ORANGE
        paint.textSize = 28f
        paint.isFakeBoldText = true
        val title = if (savedCount >= 4) "¡BIEN HECHO!" else "SE ACABO"
        val tw = paint.measureText(title)
        canvas.drawText(title, (VW - tw) / 2, 195f, paint)

        paint.color = WHITE
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("Yayos salvados: $savedCount / 6", 102f, 250f, paint)

        val msg = when {
            savedCount >= 5 -> "¡Eres el nieto del año!"
            savedCount >= 3 -> "No esta mal para empezar..."
            else -> "¡Los patinetes ganaron!"
        }
        paint.textSize = 12f
        canvas.drawText(msg, 78f, 285f, paint)

        // Big chunky RETRY button
        val retryRect = RectF(70f, 380f, 290f, 450f)
        drawRetroButton(canvas, retryRect, "RETRY", YELLOW)

        paint.color = GRAY
        paint.textSize = 10f
        canvas.drawText("Tap RETRY or go back to menu", 82f, 490f, paint)
    }

    // ==================== DATA CLASSES ====================

    private enum class GameState { MENU, PLAYING, GAME_OVER }
    private enum class Difficulty { EASY, MEDIUM, HARD }

    private enum class VehicleType { PATINETE, MOTO, COCHE, AUTOBUS }

    private data class TrafficLight(
        val x: Float, val y: Float,
        val cycleMs: Long,
        val phaseOffset: Long
    ) {
        fun isRed(frame: Long): Boolean {
            val t = (frame * 16 + phaseOffset) % cycleMs
            return t > cycleMs * 0.48f   // roughly half red (short cycles)
        }
        fun update(frame: Long) { /* nothing needed */ }
    }

    private data class Yayo(
        val id: Int,
        var x: Float,
        var y: Float,
        val baseSpeed: Float,
        val tirednessChance: Float,
        val color: Int,
        val name: String
    ) {
        var state = YayoState.ALIVE
        var tiredUntil = 0L
        var forceStopUntil = 0L
        var forceGoUntil = 0L
        var hurryUntil = 0L
        var deathOrSaveFrame = 0L
        private var lastTiredCheck = 0L

        fun update(frame: Long, isOnRoad: (Float) -> Boolean, isInGoal: (Float, Float) -> Boolean) {
            if (state != YayoState.ALIVE) return

            val nowTired = (tiredUntil > frame) || (forceStopUntil > frame)
            val canMove = !nowTired

            if (canMove) {
                var speed = baseSpeed
                if (hurryUntil > frame) speed *= 1.85f
                if (forceGoUntil > frame) speed *= 1.6f

                // Prefer going up, small horizontal wander on safe zones
                y -= speed
                if (!isOnRoad(y)) {
                    x += (Random.nextFloat() - 0.5f) * 0.6f
                }
            }

            // Random tiredness
            if (frame - lastTiredCheck > 55 && Random.nextFloat() < tirednessChance) {
                tiredUntil = frame + 95 + Random.nextInt(70)
                lastTiredCheck = frame
            }
        }

        fun isTired(frame: Long) = tiredUntil > frame || forceStopUntil > frame
    }

    private enum class YayoState { ALIVE, DEAD, SAVED }

    private data class Vehicle(
        var x: Float,
        var y: Float,
        val dir: Float,           // +1 right/down, -1 left/up
        val speed: Float,
        val type: VehicleType,
        val isVertical: Boolean = false
    ) {
        fun update(maxX: Float) {
            if (isVertical) {
                y += dir * speed
            } else {
                x += dir * speed
            }
        }

        fun collidesWith(yayo: Yayo): Boolean {
            val margin = 3f
            return if (!isVertical) {
                val vw = when (type) {
                    VehicleType.PATINETE -> 18f
                    VehicleType.MOTO -> 26f
                    VehicleType.COCHE -> 38f
                    VehicleType.AUTOBUS -> 64f
                }
                val vh = if (type == VehicleType.AUTOBUS) 22f else 16f
                RectF(x, y - vh/2, x + vw, y + vh/2).intersect(
                    RectF(yayo.x - 7, yayo.y - 11, yayo.x + 7, yayo.y + 8)
                )
            } else {
                RectF(x - 8, y, x + 8, y + 34).intersect(
                    RectF(yayo.x - 6, yayo.y - 9, yayo.x + 6, yayo.y + 7)
                )
            }
        }
    }

    private data class Bus(
        var x: Float,
        val y: Float
    ) {
        var state = BusState.ARRIVING
        var spawnedYayos = 0

        fun update() {
            when (state) {
                BusState.ARRIVING -> {
                    x += 2.8f
                    if (x > 40f) state = BusState.STOPPED
                }
                BusState.STOPPED -> {
                    // small pause then start unloading
                    if (Random.nextFloat() < 0.08f) state = BusState.UNLOADING
                }
                BusState.UNLOADING, BusState.DONE -> {}
                BusState.GONE -> {}
            }
        }
    }

    private enum class BusState { ARRIVING, STOPPED, UNLOADING, DONE, GONE }
}