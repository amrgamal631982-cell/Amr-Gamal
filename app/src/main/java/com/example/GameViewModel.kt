package com.example

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class FeedbackType {
    CORRECT, SKIP
}

class GameViewModel : ViewModel() {

    // Players list - initialized with default players so the user can play right away!
    private val _players = MutableStateFlow<List<Player>>(
        listOf(
            Player(id = UUID.randomUUID().toString(), name = "أحمد"),
            Player(id = UUID.randomUUID().toString(), name = "سارة")
        )
    )
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _currentPlayerIndex = MutableStateFlow(0)
    val currentPlayerIndex: StateFlow<Int> = _currentPlayerIndex.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    private val _gameStage = MutableStateFlow(GameStage.REGISTRATION)
    val gameStage: StateFlow<GameStage> = _gameStage.asStateFlow()

    private val _currentWord = MutableStateFlow("")
    val currentWord: StateFlow<String> = _currentWord.asStateFlow()

    private val _currentWordIndex = MutableStateFlow(1)
    val currentWordIndex: StateFlow<Int> = _currentWordIndex.asStateFlow()

    private val _currentRoundAnswers = MutableStateFlow<List<WordResult>>(emptyList())
    val currentRoundAnswers: StateFlow<List<WordResult>> = _currentRoundAnswers.asStateFlow()

    private val _currentRoundScore = MutableStateFlow(0)
    val currentRoundScore: StateFlow<Int> = _currentRoundScore.asStateFlow()

    private val _showHowToPlay = MutableStateFlow(false)
    val showHowToPlay: StateFlow<Boolean> = _showHowToPlay.asStateFlow()

    // Countdown before round starts (3, 2, 1...)
    private val _startCountdown = MutableStateFlow(3)
    val startCountdown: StateFlow<Int> = _startCountdown.asStateFlow()

    // Feedback state (CORRECT/SKIP) to flash screen colors
    private val _feedbackState = MutableStateFlow<FeedbackType?>(null)
    val feedbackState: StateFlow<FeedbackType?> = _feedbackState.asStateFlow()

    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var isActionCooldown = false

    private val wordsPool = mutableListOf<String>()

    // Tone generator for self-contained, high-performance sound synthesis
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCorrectSound() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSkipSound() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONGESTION, 180)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playTickSound() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 60)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playVictorySound() {
        viewModelScope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                delay(150)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                delay(150)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Action Methods ---

    fun addPlayer(name: String) {
        if (name.trim().isNotEmpty()) {
            val newList = _players.value + Player(id = UUID.randomUUID().toString(), name = name.trim())
            _players.value = newList
        }
    }

    fun removePlayer(player: Player) {
        val newList = _players.value.filter { it.id != player.id }
        _players.value = newList
    }

    fun toggleHowToPlay(show: Boolean) {
        _showHowToPlay.value = show
    }

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
        // Go to READY stage to let the player prepare before starting the timer
        _gameStage.value = GameStage.ROUND_READY
        startReadyCountdown()
    }

    private fun startReadyCountdown() {
        countdownJob?.cancel()
        _startCountdown.value = 3
        countdownJob = viewModelScope.launch {
            while (_startCountdown.value > 1) {
                delay(1000)
                _startCountdown.value -= 1
                playTickSound()
            }
            delay(1000)
            startGameplay()
        }
    }

    private fun startGameplay() {
        val category = _selectedCategory.value ?: return
        wordsPool.clear()
        wordsPool.addAll(category.words.shuffled())
        
        _currentRoundAnswers.value = emptyList()
        _currentRoundScore.value = 0
        _currentWordIndex.value = 1
        _feedbackState.value = null
        isActionCooldown = false

        if (wordsPool.isNotEmpty()) {
            _currentWord.value = wordsPool.removeAt(0)
        } else {
            _currentWord.value = "انتهت الكلمات!"
        }

        _gameStage.value = GameStage.GAMEPLAY
    }

    fun recordCorrect() {
        if (isActionCooldown || _gameStage.value != GameStage.GAMEPLAY) return
        isActionCooldown = true
        
        playCorrectSound()
        
        val answeredWord = _currentWord.value
        val result = WordResult(word = answeredWord, isCorrect = true)
        _currentRoundAnswers.value = _currentRoundAnswers.value + result
        _currentRoundScore.value += 1

        viewModelScope.launch {
            _feedbackState.value = FeedbackType.CORRECT
            delay(1000) // Visual confirmation delay
            _feedbackState.value = null
            
            // Next word
            if (_currentWordIndex.value >= 10) {
                endRound()
            } else {
                _currentWordIndex.value += 1
                nextWord()
            }
            isActionCooldown = false
        }
    }

    fun recordSkip() {
        if (isActionCooldown || _gameStage.value != GameStage.GAMEPLAY) return
        isActionCooldown = true
        
        playSkipSound()
        
        val answeredWord = _currentWord.value
        val result = WordResult(word = answeredWord, isCorrect = false)
        _currentRoundAnswers.value = _currentRoundAnswers.value + result

        viewModelScope.launch {
            _feedbackState.value = FeedbackType.SKIP
            delay(1000) // Visual confirmation delay
            _feedbackState.value = null
            
            // Next word
            if (_currentWordIndex.value >= 10) {
                endRound()
            } else {
                _currentWordIndex.value += 1
                nextWord()
            }
            isActionCooldown = false
        }
    }

    private fun nextWord() {
        if (wordsPool.isNotEmpty()) {
            _currentWord.value = wordsPool.removeAt(0)
        } else {
            // Re-shuffle category words if they run out during the 60s
            val category = _selectedCategory.value
            if (category != null) {
                wordsPool.addAll(category.words.shuffled())
                if (wordsPool.isNotEmpty()) {
                    _currentWord.value = wordsPool.removeAt(0)
                }
            } else {
                _currentWord.value = "انتهت الكلمات!"
            }
        }
    }

    private fun endRound() {
        _feedbackState.value = null
        
        // Save score to player
        val index = _currentPlayerIndex.value
        val currentPlayers = _players.value
        if (index in currentPlayers.indices) {
            val updatedPlayer = currentPlayers[index].copy(
                score = _currentRoundScore.value,
                wordResults = _currentRoundAnswers.value
            )
            val updatedList = currentPlayers.toMutableList()
            updatedList[index] = updatedPlayer
            _players.value = updatedList
        }

        _gameStage.value = GameStage.ROUND_SUMMARY
        playVictorySound()
    }

    fun nextTurn() {
        val nextIndex = _currentPlayerIndex.value + 1
        if (nextIndex < _players.value.size) {
            _currentPlayerIndex.value = nextIndex
            _selectedCategory.value = null
            _gameStage.value = GameStage.CATEGORY_SELECTION
        } else {
            // All players done, go to PODIUM!
            _gameStage.value = GameStage.PODIUM
            playVictorySound()
        }
    }

    fun restartGame() {
        // Reset all player scores to 0
        val resetPlayers = _players.value.map { it.copy(score = 0, wordResults = emptyList()) }
        _players.value = resetPlayers
        _currentPlayerIndex.value = 0
        _selectedCategory.value = null
        _gameStage.value = GameStage.CATEGORY_SELECTION
    }

    fun backToMainMenu() {
        // Reset and go to Registration
        val resetPlayers = _players.value.map { it.copy(score = 0, wordResults = emptyList()) }
        _players.value = resetPlayers
        _currentPlayerIndex.value = 0
        _selectedCategory.value = null
        _gameStage.value = GameStage.REGISTRATION
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        countdownJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
