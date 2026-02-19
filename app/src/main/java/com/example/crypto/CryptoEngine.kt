object CryptoEngine {
    // Дефолтный список слов на случай, если словарь не передан
    private val DEFAULT_WORDS = listOf(
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel", "india",
        "juliet", "kilo", "lima", "mike", "november", "oscar", "papa", "quebec", "romeo",
        "sierra", "tango", "uniform", "victor", "whiskey", "xray", "yankee", "zulu",
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd",
        "abuse", "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire"
    )

    // ИСПРАВЛЕНИЕ: Функция теперь явно принимает словарь
    fun generateMnemonic(dictionary: List<String> = emptyList()): List<String> {
        // Если словарь пустой или null, используем дефолтный
        val source = if (dictionary.isNotEmpty()) dictionary else DEFAULT_WORDS
        val random = java.security.SecureRandom()
        // Генерируем 25 слов
        return (1..25).map { source[random.nextInt(source.size)] }
    }

    // Заглушка шифрования (симуляция)
    fun encrypt(data: ByteArray, mnemonic: List<String>, extension: String): ByteArray {
        val prefix = "ENC|$extension|".toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        // В Kotlin оператор + для массивов создает новый массив (объединяет их)
        return prefix + data
    }

    // Расшифровка
    fun decrypt(data: ByteArray, mnemonic: List<String>): DecryptResult {
        // Берем первые 50 байт, чтобы проверить заголовок
        val header = String(data.take(50).toByteArray(), java.nio.charset.StandardCharsets.UTF_8)

        if (header.startsWith("ENC|")) {
            val parts = header.split("|")
            // parts[0] = "ENC", parts[1] = расширение, parts[2] = пусто (из-за последнего |)
            val ext = parts[1]
            val headerSize = "ENC|$ext|".toByteArray(java.nio.charset.StandardCharsets.UTF_8).size

            // Возвращаем данные без заголовка
            return DecryptResult(data.drop(headerSize).toByteArray(), ext)
        }
        throw Exception("Invalid File Format or Wrong Key")
    }

    data class DecryptResult(val data: ByteArray, val extension: String)
}