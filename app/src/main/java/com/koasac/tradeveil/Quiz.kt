package com.koasac.tradeveil

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.koasac.tradeveil.services.AdManager
import com.koasac.tradeveil.services.com.example.tradeveil.data.Question
import com.koasac.tradeveil.services.com.example.tradeveil.utils.JsonParser
import com.koasac.tradeveil.services.com.example.tradeveil.utils.QuizConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class Quiz : AppCompatActivity() {

    private lateinit var tvQuestionText: TextView
    private lateinit var tvOption1: TextView
    private lateinit var tvOption2: TextView
    private lateinit var tvOption3: TextView
    private lateinit var tvOption4: TextView
    private lateinit var rbOption1: RadioButton
    private lateinit var rbOption2: RadioButton
    private lateinit var rbOption3: RadioButton
    private lateinit var rbOption4: RadioButton
    private lateinit var radioGroup: RadioGroup
    private lateinit var btnNext: Button
    private lateinit var tvTotalQuestions: TextView
    private lateinit var tvTimer: TextView
    private lateinit var pbTimer: ProgressBar
    private lateinit var cvQuestionCard: CardView
    private lateinit var tvAttempts: TextView

    // Option card containers
    private lateinit var option1Container: LinearLayout
    private lateinit var option2Container: LinearLayout
    private lateinit var option3Container: LinearLayout
    private lateinit var option4Container: LinearLayout

    // Hidden radio buttons for proper grouping
    private lateinit var rbHidden1: RadioButton
    private lateinit var rbHidden2: RadioButton
    private lateinit var rbHidden3: RadioButton
    private lateinit var rbHidden4: RadioButton

    private lateinit var questions: List<Question>
    private lateinit var currentQuestion: Question
    private var currentCorrectAnswerKey: String = ""
    private var timeLeftInMillis: Long = QuizConstants.TIME_PER_QUESTION
    private var timer: CountDownTimer? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentAttempt = 1
    private var isAnsweredCorrectly = false
    private var pointsEarned = 0
    private var isQuizCompleted = false
    private var isProcessingAnswer = false
    private var timerPulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize AdManager
        AdManager.initialize(this)

        initViews()
        loadQuestion()
        setupAnimations()
        setupClickListeners()
    }

    private fun initViews() {
        tvQuestionText = findViewById(R.id.tvQuestionText)
        tvOption1 = findViewById(R.id.tvOption1)
        tvOption2 = findViewById(R.id.tvOption2)
        tvOption3 = findViewById(R.id.tvOption3)
        tvOption4 = findViewById(R.id.tvOption4)
        rbOption1 = findViewById(R.id.rbOption1)
        rbOption2 = findViewById(R.id.rbOption2)
        rbOption3 = findViewById(R.id.rbOption3)
        rbOption4 = findViewById(R.id.rbOption4)

        // Get option containers (LinearLayouts inside CardViews)
        option1Container = rbOption1.parent as LinearLayout
        option2Container = rbOption2.parent as LinearLayout
        option3Container = rbOption3.parent as LinearLayout
        option4Container = rbOption4.parent as LinearLayout

        // Hidden RadioGroup for proper selection behavior
        radioGroup = findViewById(R.id.radioGroup)
        rbHidden1 = findViewById(R.id.rbHidden1)
        rbHidden2 = findViewById(R.id.rbHidden2)
        rbHidden3 = findViewById(R.id.rbHidden3)
        rbHidden4 = findViewById(R.id.rbHidden4)

        btnNext = findViewById(R.id.btnNext)
        tvTimer = findViewById(R.id.tvTimer)
        pbTimer = findViewById(R.id.pbTimer)
        cvQuestionCard = findViewById(R.id.cvQuestionCard)
        tvAttempts = findViewById(R.id.tvAttempts)
        tvTotalQuestions = findViewById(R.id.tvTotalQuestions)

        tvTotalQuestions.text = "/1"
        updateAttemptsDisplay()
    }

    private fun timeUp() {
        isProcessingAnswer = true
        timer?.cancel()
        disableOptions()

        // Check if this was the final attempt BEFORE incrementing
        if (currentAttempt >= QuizConstants.MAX_ATTEMPTS) {
            highlightCorrectAnswer()
            pointsEarned = 0
            btnNext.text = "Finish"
            isQuizCompleted = true

            // Show ad after final attempt (time up)
            showLastAttemptAd()
        } else {
            // Just show that time is up, don't reveal correct answer
            showTimeUpFeedback()
            currentAttempt++ // Increment AFTER checking
            btnNext.text = "Try Again"
            updateAttemptsDisplay()

            // Show attempts remaining after time up
            showAttemptsRemainingToast()
        }

        isProcessingAnswer = false
    }

    private fun checkAnswer() {
        if (isProcessingAnswer) return

        val selectedOptionKey = getSelectedOption()

        if (selectedOptionKey == null) {
            val shakeAnim = ObjectAnimator.ofFloat(btnNext, "translationX", 0f, 10f, -10f, 10f, -10f, 0f).apply {
                duration = 500
            }
            shakeAnim.start()
            return
        }

        isProcessingAnswer = true
        timer?.cancel()
        disableOptions()

        if (selectedOptionKey == currentCorrectAnswerKey) {
            // Correct answer - show correct answer and finish
            isAnsweredCorrectly = true
            pointsEarned = QuizConstants.ATTEMPT_POINTS[currentAttempt - 1]
            highlightCorrectAnswer()
            btnNext.text = "Finish"
            isQuizCompleted = true
        } else {
            // Incorrect answer
            highlightIncorrectAnswer(selectedOptionKey)

            // Check if this was the final attempt BEFORE incrementing
            if (currentAttempt >= QuizConstants.MAX_ATTEMPTS) {
                // Final attempt failed - now show correct answer
                highlightCorrectAnswer()
                pointsEarned = 0
                btnNext.text = "Finish"
                isQuizCompleted = true

                // Show ad after final attempt (incorrect answer)
                showLastAttemptAd()
            } else {
                // Don't show correct answer yet, let them try again
                currentAttempt++ // Increment AFTER checking
                btnNext.text = "Try Again"
                updateAttemptsDisplay()

                // Show attempts remaining after incorrect answer
                showAttemptsRemainingToast()
            }
        }

        isProcessingAnswer = false
    }

    private fun showLastAttemptAd() {
        // Show interstitial ad after last attempt
        if (AdManager.isQuizInterstitialAdReady()) {
            AdManager.showInterstitialAd(this) {
                // Ad dismissed, continue with quiz flow
            }
        }
    }

    private fun updateAttemptsDisplay() {
        tvAttempts.text = "Attempt: $currentAttempt/${QuizConstants.MAX_ATTEMPTS}"
    }

    private fun loadQuestion() {
        questions = JsonParser.parseQuestions(this).shuffled().take(1)
        if (questions.isNotEmpty()) {
            currentQuestion = questions.first()
            displayQuestion()
        } else {
            Toast.makeText(this, "No questions available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupAnimations() {
        cvQuestionCard.cameraDistance = 8000f * resources.displayMetrics.density

        // Smoother timer pulse animation
        timerPulseAnimator = ObjectAnimator.ofFloat(tvTimer, "scaleX", 1f, 1.05f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        timerPulseAnimator?.start()
    }

    private fun displayQuestion() {
        // Smoother card flip animation
        val flipOut = ObjectAnimator.ofFloat(cvQuestionCard, "rotationY", 0f, 90f).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
        }

        flipOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tvQuestionText.text = currentQuestion.question
                tvOption1.text = currentQuestion.A
                tvOption2.text = currentQuestion.B
                tvOption3.text = currentQuestion.C
                tvOption4.text = currentQuestion.D
                currentCorrectAnswerKey = currentQuestion.answer

                clearSelections()
                resetOptionCards()
                enableOptions()

                val flipIn = ObjectAnimator.ofFloat(cvQuestionCard, "rotationY", 90f, 0f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                }
                flipIn.start()
            }
        })

        flipOut.start()
        startTimer()
    }

    private fun clearSelections() {
        // Clear hidden RadioGroup
        radioGroup.clearCheck()

        // Clear visible radio buttons
        rbOption1.isChecked = false
        rbOption2.isChecked = false
        rbOption3.isChecked = false
        rbOption4.isChecked = false

        // Clear hidden radio buttons
        rbHidden1.isChecked = false
        rbHidden2.isChecked = false
        rbHidden3.isChecked = false
        rbHidden4.isChecked = false
    }

    private fun selectOption(optionNumber: Int) {
        if (isProcessingAnswer) return

        // Clear all selections first
        clearSelections()

        // Set the selected option
        when (optionNumber) {
            1 -> {
                rbOption1.isChecked = true
                rbHidden1.isChecked = true
                animateSelection(option1Container)
            }
            2 -> {
                rbOption2.isChecked = true
                rbHidden2.isChecked = true
                animateSelection(option2Container)
            }
            3 -> {
                rbOption3.isChecked = true
                rbHidden3.isChecked = true
                animateSelection(option3Container)
            }
            4 -> {
                rbOption4.isChecked = true
                rbHidden4.isChecked = true
                animateSelection(option4Container)
            }
        }
    }

    private fun animateSelection(container: LinearLayout) {
        val card = container.parent as CardView
        val scaleAnim = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.02f, 1f).apply {
            duration = 200
        }
        scaleAnim.start()
    }

    private fun resetTimer() {
        timer?.cancel()
        timeLeftInMillis = QuizConstants.TIME_PER_QUESTION
        pbTimer.max = (QuizConstants.TIME_PER_QUESTION / 1000).toInt()
        pbTimer.progress = (QuizConstants.TIME_PER_QUESTION / 1000).toInt()
        tvTimer.text = (QuizConstants.TIME_PER_QUESTION / 1000).toString()
        tvTimer.setTextColor(Color.BLACK)
    }

    private fun startTimer() {
        resetTimer()

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                val seconds = millisUntilFinished / 1000
                tvTimer.text = seconds.toString()
                pbTimer.progress = seconds.toInt()

                if (seconds <= 10) {
                    tvTimer.setTextColor(Color.RED)
                    // Smoother warning animation
                    if (seconds <= 5) {
                        val warningAnim = ObjectAnimator.ofFloat(tvTimer, "alpha", 1f, 0.3f, 1f).apply {
                            duration = 500
                        }
                        warningAnim.start()
                    }
                }
            }

            override fun onFinish() {
                if (!isProcessingAnswer && !isQuizCompleted) {
                    timeUp()
                }
            }
        }.start()
    }

    private fun showTimeUpFeedback() {
        // Show generic time up message without revealing correct answer
        Toast.makeText(this, "Time's up! Try again.", Toast.LENGTH_SHORT).show()

        // Add a subtle shake to all options to indicate time up
        val optionCards = getOptionCards()

        optionCards.forEach { card ->
            val shakeAnim = ObjectAnimator.ofFloat(card, "translationX", 0f, 5f, -5f, 0f).apply {
                duration = 300
            }
            shakeAnim.start()
        }
    }

    private fun getOptionCards(): List<CardView> {
        // Get CardView parents of each option
        return listOf(
            option1Container.parent as CardView,
            option2Container.parent as CardView,
            option3Container.parent as CardView,
            option4Container.parent as CardView
        )
    }

    private fun highlightCorrectAnswer() {
        when (currentCorrectAnswerKey) {
            "A" -> animateOptionCorrect(tvOption1)
            "B" -> animateOptionCorrect(tvOption2)
            "C" -> animateOptionCorrect(tvOption3)
            "D" -> animateOptionCorrect(tvOption4)
        }
    }

    private fun animateOptionCorrect(textView: TextView) {
        val cardView = textView.parent.parent as CardView
        cardView.setCardBackgroundColor(Color.parseColor("#4CAF50"))

        val animator = ObjectAnimator.ofFloat(cardView, "scaleX", 1f, 1.03f, 1f).apply {
            duration = 600
            repeatCount = 2
        }
        animator.start()
    }

    private fun animateOptionIncorrect(textView: TextView) {
        val cardView = textView.parent.parent as CardView
        cardView.setCardBackgroundColor(Color.parseColor("#F44336"))

        val shakeAnim = ObjectAnimator.ofFloat(cardView, "translationX", 0f, 8f, -8f, 8f, -8f, 0f).apply {
            duration = 400
        }
        shakeAnim.start()
    }

    private fun disableOptions() {
        option1Container.isEnabled = false
        option2Container.isEnabled = false
        option3Container.isEnabled = false
        option4Container.isEnabled = false
        rbOption1.isEnabled = false
        rbOption2.isEnabled = false
        rbOption3.isEnabled = false
        rbOption4.isEnabled = false
    }

    private fun enableOptions() {
        option1Container.isEnabled = true
        option2Container.isEnabled = true
        option3Container.isEnabled = true
        option4Container.isEnabled = true
        rbOption1.isEnabled = true
        rbOption2.isEnabled = true
        rbOption3.isEnabled = true
        rbOption4.isEnabled = true
    }

    private fun setupClickListeners() {
        btnNext.setOnClickListener {
            if (isProcessingAnswer) return@setOnClickListener

            when (btnNext.text.toString()) {
                "Next" -> {
                    AdManager.showInterstitialAd(this) {
                        checkAnswer()
                    }
                }
                "Try Again" -> {
                    resetForNextAttempt()
                }
                "Finish" -> {
                    completeQuiz()
                }
            }
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            showExitDialog()
        }

        // Set up click listeners for option containers
        option1Container.setOnClickListener {
            selectOption(1)
        }

        option2Container.setOnClickListener {
            selectOption(2)
        }

        option3Container.setOnClickListener {
            selectOption(3)
        }

        option4Container.setOnClickListener {
            selectOption(4)
        }

        // Hidden RadioGroup listener for backup
        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            if (!isProcessingAnswer && checkedId != -1) {
                // Sync visible radio buttons with hidden ones
                when (checkedId) {
                    rbHidden1.id -> {
                        rbOption1.isChecked = true
                        rbOption2.isChecked = false
                        rbOption3.isChecked = false
                        rbOption4.isChecked = false
                    }
                    rbHidden2.id -> {
                        rbOption1.isChecked = false
                        rbOption2.isChecked = true
                        rbOption3.isChecked = false
                        rbOption4.isChecked = false
                    }
                    rbHidden3.id -> {
                        rbOption1.isChecked = false
                        rbOption2.isChecked = false
                        rbOption3.isChecked = true
                        rbOption4.isChecked = false
                    }
                    rbHidden4.id -> {
                        rbOption1.isChecked = false
                        rbOption2.isChecked = false
                        rbOption3.isChecked = false
                        rbOption4.isChecked = true
                    }
                }
            }
        }
    }

    private fun getSelectedOption(): String? {
        // Check visible radio buttons
        return when {
            rbOption1.isChecked -> "A"
            rbOption2.isChecked -> "B"
            rbOption3.isChecked -> "C"
            rbOption4.isChecked -> "D"
            else -> {
                // Fallback: check hidden radio group
                when (radioGroup.checkedRadioButtonId) {
                    rbHidden1.id -> "A"
                    rbHidden2.id -> "B"
                    rbHidden3.id -> "C"
                    rbHidden4.id -> "D"
                    else -> null
                }
            }
        }
    }

    private fun highlightIncorrectAnswer(selectedKey: String) {
        val selectedTextView = when (selectedKey) {
            "A" -> tvOption1
            "B" -> tvOption2
            "C" -> tvOption3
            "D" -> tvOption4
            else -> return
        }
        animateOptionIncorrect(selectedTextView)
    }

    private fun resetForNextAttempt() {
        if (isProcessingAnswer) return

        clearSelections()
        enableOptions()
        resetOptionCards()
        btnNext.text = "Next"
        isProcessingAnswer = false
        startTimer()
    }

    private fun resetOptionCards() {
        val optionCards = getOptionCards()

        optionCards.forEach { card ->
            card.setCardBackgroundColor(Color.WHITE)
        }
    }

    private fun completeQuiz() {
        if (isFinishing || isDestroyed) return

        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }

        // Prevent multiple completions
        if (btnNext.text == "Saving...") return

        // Show loading state
        btnNext.isEnabled = false
        btnNext.text = "Saving..."

        try {
            // Update points if earned
            if (pointsEarned > 0) {
                db.collection("users").document(currentUser.uid)
                    .update("points", FieldValue.increment(pointsEarned.toLong()))
                    .addOnFailureListener { e ->
                    }
            }

            // Update quiz stats
            val updates = hashMapOf<String, Any>(
                "lastAttempt" to FieldValue.serverTimestamp(),
                "completed" to FieldValue.increment(1)
            )

            db.collection("users").document(currentUser.uid)
                .collection("quizProgress")
                .document("stats")
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    if (!isFinishing && !isDestroyed) {
                        fetchCompletedCountAndNavigate(currentUser.uid)
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        navigateToResult(0) // Navigate with default value
                    }
                }

        } catch (e: Exception) {
            if (!isFinishing && !isDestroyed) {
                navigateToResult(0)
            }
        }
    }

    private fun fetchCompletedCountAndNavigate(userId: String) {
        db.collection("users").document(userId)
            .collection("quizProgress")
            .document("stats")
            .get()
            .addOnSuccessListener { document ->
                if (!isFinishing && !isDestroyed) {
                    val completed = document?.getLong("completed")?.toInt() ?: 0
                    navigateToResult(completed)
                }
            }
            .addOnFailureListener { e ->
                if (!isFinishing && !isDestroyed) {
                    navigateToResult(0)
                }
            }
    }

    private fun navigateToResult(totalCompleted: Int) {
        if (isFinishing || isDestroyed) {
            return
        }

        try {
            val intent = Intent(this, QuizResult::class.java).apply {
                putExtra("points", pointsEarned)
                putExtra("attempts", if (isAnsweredCorrectly) currentAttempt else QuizConstants.MAX_ATTEMPTS)
                putExtra("totalCompleted", totalCompleted)
                putExtra("isCorrect", isAnsweredCorrectly)

                // Add flags to prevent issues
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            startActivity(intent)
            finish()

        } catch (e: Exception) {
            finish()
        }
    }

    private fun showAttemptsRemainingToast() {
        val attemptsLeft = QuizConstants.MAX_ATTEMPTS - currentAttempt + 1
        if (attemptsLeft > 0) {
            val message = if (attemptsLeft == 1) {
                "Last attempt remaining!"
            } else {
                "$attemptsLeft attempts remaining!"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExitDialog() {
        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("Leave Quiz?")
            .setContentText("Your progress will not be saved")
            .setConfirmText("Leave")
            .setCancelText("Stay")
            .setConfirmClickListener {
                it.dismissWithAnimation()
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timerPulseAnimator?.cancel()
    }

    override fun onStop() {
        super.onStop()
        // Pause timer when activity is not visible
        timer?.cancel()
    }
}