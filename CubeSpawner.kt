import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

// Аналог UnityEngine.Vector3, но с неизменяемыми полями
@Serializable
data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
}

// Данные для сохранения в JSON
@Serializable
data class SaveData(
    val counter: Int,
    val cubes: List<Vector3>
)

class CubeSpawner {
    // Настройки (аналогично полям с [Header])
    var cubePrefabAvailable = true // Вместо префаба – просто флаг, что куб может создаваться
    val interactiveCubePosition = Vector3(0f, 0f, 2f) // Позиция интерактивного куба
    val interactDistance = 3f
    val spawnRadius = 2f

    // Счётчик созданных кубов (blow)
    var blow: Int = 0
        private set

    // Список позиций созданных кубов
    private val spawnedCubes = mutableListOf<Vector3>()
    private val saveFile = File("cubes_save.json")
    private val json = Json { prettyPrint = true }

    init {
        loadCubes()
    }

    // Имитация Update: вызов из игрового цикла или по команде пользователя
    fun processInput(input: String) {
        if (input.trim().uppercase() == "E") {
            tryInteract()
        }
    }

    // Проверяем, попадает ли взгляд игрока в интерактивный куб
    private fun tryInteract() {
        // Рейкаст из "камеры" (0,0,0) в направлении взгляда – вперёд (0,0,1)
        val cameraPos = Vector3(0f, 0f, 0f)
        val cameraDir = Vector3(0f, 0f, 1f) // смотрит вдоль Z

        // Простейшая проверка пересечения луча с кубом размером 1x1x1
        if (rayHitsCube(cameraPos, cameraDir, interactiveCubePosition, 1f)) {
            spawnCube()
        } else {
            log("Интерактивный куб вне поля зрения или слишком далеко.")
        }
    }

    // Простой AABB-тест луча и единичного куба
    private fun rayHitsCube(rayOrigin: Vector3, rayDir: Vector3, cubeCenter: Vector3, cubeHalfSize: Float): Boolean {
        val min = Vector3(cubeCenter.x - cubeHalfSize, cubeCenter.y - cubeHalfSize, cubeCenter.z - cubeHalfSize)
        val max = Vector3(cubeCenter.x + cubeHalfSize, cubeCenter.y + cubeHalfSize, cubeCenter.z + cubeHalfSize)

        var tmin = Float.NEGATIVE_INFINITY
        var tmax = Float.POSITIVE_INFINITY

        val dirX = if (rayDir.x != 0f) rayDir.x else 1e-6f
        val dirY = if (rayDir.y != 0f) rayDir.y else 1e-6f
        val dirZ = if (rayDir.z != 0f) rayDir.z else 1e-6f

        val t1 = (min.x - rayOrigin.x) / dirX
        val t2 = (max.x - rayOrigin.x) / dirX
        tmin = maxOf(tmin, minOf(t1, t2))
        tmax = minOf(tmax, maxOf(t1, t2))

        val t3 = (min.y - rayOrigin.y) / dirY
        val t4 = (max.y - rayOrigin.y) / dirY
        tmin = maxOf(tmin, minOf(t3, t4))
        tmax = minOf(tmax, maxOf(t3, t4))

        val t5 = (min.z - rayOrigin.z) / dirZ
        val t6 = (max.z - rayOrigin.z) / dirZ
        tmin = maxOf(tmin, minOf(t5, t6))
        tmax = minOf(tmax, maxOf(t5, t6))

        return tmax >= tmin && tmax > 0 && tmin <= interactDistance
    }

    private fun spawnCube() {
        if (!cubePrefabAvailable) {
            log("Ошибка: префаб куба недоступен!")
            return
        }

        // Случайное смещение вокруг интерактивного куба
        val randomAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val randomRadius = Random.nextFloat() * spawnRadius
        val offsetX = randomRadius * kotlin.math.cos(randomAngle)
        val offsetZ = randomRadius * kotlin.math.sin(randomAngle)
        val randomOffset = Vector3(offsetX, 0f, offsetZ) // y = 0, чтобы кубы не «взлетали»
        val spawnPos = interactiveCubePosition + randomOffset

        // Создаём куб (добавляем позицию в список)
        spawnedCubes.add(spawnPos)
        blow++
        log("[Spawn] Создан куб #$blow в позиции $spawnPos")

        saveCubes()
    }

    private fun saveCubes() {
        val data = SaveData(blow, spawnedCubes.toList())
        val jsonString = json.encodeToString(data)
        saveFile.writeText(jsonString)
        log("[Save] Данные сохранены: $jsonString")
    }

    private fun loadCubes() {
        if (saveFile.exists()) {
            val jsonString = saveFile.readText()
            val data = json.decodeFromString<SaveData>(jsonString)
            blow = data.counter
            spawnedCubes.clear()
            spawnedCubes.addAll(data.cubes)
            log("[Load] Загружено сохранение. Счётчик: $blow, кубов: ${data.cubes.size}")
            data.cubes.forEachIndexed { index, pos ->
                log("[Load] Восстановлен куб ${index + 1} в $pos")
            }
        } else {
            log("[Load] Файл сохранения не найден, начальное состояние.")
        }
    }

    // Логирование и вывод на экран
    private fun log(message: String) {
        println(message)
    }

    fun showUI() {
        println("Создано кубов (blow): $blow")
    }
}

// Точка входа в консольное приложение
fun main() {
    val spawner = CubeSpawner()
    println("=== Интерактивный спавнер кубов ===")
    println("Нажмите E и Enter, чтобы взаимодействовать с кубом.")
    println("Для выхода введите 'q' или 'exit'.")
    spawner.showUI()

    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break
        if (input.equals("q", ignoreCase = true) || input.equals("exit", ignoreCase = true)) {
            println("Завершение работы.")
            break
        }
        spawner.processInput(input)
        spawner.showUI()
    }
}