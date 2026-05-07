package com.chore.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.CreateRpsGameRequest
import com.chore.tracker.data.Member
import com.chore.tracker.data.PlayRpsRequest
import com.chore.tracker.data.Repo
import com.chore.tracker.data.RpsGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private enum class RpsScreenMode { LOBBY, MULTI, SOLO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpsScreen(repo: Repo, onBack: () -> Unit) {
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(RpsScreenMode.LOBBY) }
    var activeGameId by remember { mutableStateOf<String?>(null) }

    // Light auto-refresh while in multi-user mode so the opponent's submission lands.
    LaunchedEffect(mode, activeGameId) {
        if (mode == RpsScreenMode.MULTI && activeGameId != null) {
            while (true) {
                delay(2500)
                repo.refreshRewardsAndRps()
            }
        }
    }

    // Resolve the active game from refreshed state.
    val activeGame = remember(state.rpsGames, activeGameId) {
        activeGameId?.let { id -> state.rpsGames.firstOrNull { it.id == id } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("🪨  📄  ✂️", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == RpsScreenMode.LOBBY) onBack()
                        else { mode = RpsScreenMode.LOBBY; activeGameId = null }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                RpsScreenMode.LOBBY -> RpsLobby(
                    allMembers = state.members,
                    activeMultiGames = state.rpsGames.filter { it.status == "in_progress" },
                    currentUserId = state.currentUserId,
                    onChallenge = { opponentId ->
                        scope.launch {
                            runCatching {
                                repo.api.createRpsGame(CreateRpsGameRequest(opponentId))
                            }.onSuccess { game ->
                                repo.refreshRewardsAndRps()
                                activeGameId = game.id
                                mode = RpsScreenMode.MULTI
                            }.onFailure { snackbar.showSnackbar("Couldn't start: ${it.message}") }
                        }
                    },
                    onResumeGame = { game ->
                        activeGameId = game.id
                        mode = RpsScreenMode.MULTI
                    },
                    onPlaySolo = { mode = RpsScreenMode.SOLO },
                )
                RpsScreenMode.MULTI -> {
                    val game = activeGame
                    if (game == null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Loading game…", fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        RpsMultiGame(
                            game = game,
                            currentUserId = state.currentUserId.orEmpty(),
                            members = state.members,
                            onPlay = { choice ->
                                scope.launch {
                                    runCatching {
                                        repo.api.playRps(game.id, PlayRpsRequest(choice))
                                    }.onSuccess { repo.refreshRewardsAndRps() }
                                        .onFailure { snackbar.showSnackbar("Play failed: ${it.message}") }
                                }
                            },
                            onDone = {
                                scope.launch { repo.refreshRewardsAndRps() }
                                mode = RpsScreenMode.LOBBY
                                activeGameId = null
                            },
                        )
                    }
                }
                RpsScreenMode.SOLO -> RpsSoloGame(
                    onExit = { mode = RpsScreenMode.LOBBY },
                )
            }
        }
    }
}

// ─── LOBBY ───────────────────────────────────────────────────────────────────

@Composable
private fun RpsLobby(
    allMembers: List<Member>,
    activeMultiGames: List<RpsGame>,
    currentUserId: String?,
    onChallenge: (String) -> Unit,
    onResumeGame: (RpsGame) -> Unit,
    onPlaySolo: () -> Unit,
) {
    val challengeable = allMembers.filter { it.id != currentUserId }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pixel-art banner row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelSprite(grid = ROCK_GRID, palette = ROCK_PALETTE, pixelSize = 6.dp)
            PixelSprite(grid = PAPER_GRID, palette = PAPER_PALETTE, pixelSize = 6.dp)
            PixelSprite(grid = SCISSORS_GRID, palette = SCISSORS_PALETTE, pixelSize = 6.dp)
        }

        Text(
            "Best of 3 rounds.\nWinner picks the next household reward.",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (activeMultiGames.isNotEmpty()) {
            Text(
                "Active challenges",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            activeMultiGames.forEach { game ->
                val opponentId = if (game.challengerId == currentUserId) game.opponentId else game.challengerId
                val opponent = allMembers.firstOrNull { it.id == opponentId }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResumeGame(game) }
                        .testTag("rpsActiveGame:${game.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "vs ${opponent?.displayName ?: "?"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                "Round ${game.currentRound} · " +
                                    if (game.challengerId == currentUserId)
                                        "${game.challengerScore} - ${game.opponentScore}"
                                    else "${game.opponentScore} - ${game.challengerScore}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text("▶", fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Challenge a household member",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        if (challengeable.isEmpty()) {
            Text(
                "No other household members yet — invite one to play!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            challengeable.forEach { m ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChallenge(m.id) }
                        .testTag("rpsChallenge:${m.displayName}"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AvatarBadge(
                            userId = m.id,
                            avatarVersion = m.avatarVersion,
                            fallbackText = m.displayName.take(1).uppercase(),
                            size = 32,
                        )
                        Text(
                            m.displayName,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("⚔️", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPlaySolo,
            modifier = Modifier.fillMaxWidth().testTag("rpsPlaySolo"),
        ) { Text("Play vs CPU (for fun)", fontFamily = FontFamily.Monospace) }
    }
}

// ─── MULTI-USER GAME ─────────────────────────────────────────────────────────

@Composable
private fun RpsMultiGame(
    game: RpsGame,
    currentUserId: String,
    members: List<Member>,
    onPlay: (String) -> Unit,
    onDone: () -> Unit,
) {
    val isChallenger = game.challengerId == currentUserId
    val myScore = if (isChallenger) game.challengerScore else game.opponentScore
    val theirScore = if (isChallenger) game.opponentScore else game.challengerScore
    val opponentId = if (isChallenger) game.opponentId else game.challengerId
    val opponent = members.firstOrNull { it.id == opponentId }
    val me = members.firstOrNull { it.id == currentUserId }

    val currentRound = game.rounds.firstOrNull { it.roundNumber == game.currentRound }
    val myChoice = currentRound?.let { if (isChallenger) it.challengerChoice else it.opponentChoice }
    val theirChoiceRaw = currentRound?.let { if (isChallenger) it.opponentChoice else it.challengerChoice }
    val resolved = currentRound?.resolvedAt != null
    val theirChoice = if (resolved) theirChoiceRaw else null

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Scoreboard(
            meName = me?.displayName ?: "You",
            theirName = opponent?.displayName ?: "?",
            meScore = myScore,
            theirScore = theirScore,
            roundNumber = game.currentRound,
        )

        if (game.status == "finished") {
            val youWon = game.winnerId == currentUserId
            Spacer(Modifier.height(8.dp))
            Text(
                if (youWon) "🏆 YOU WON THE GAME!" else "💀 YOU LOST",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (youWon && game.purpose == "pick_reward") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🎯 You can now pick the next household reward.",
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().testTag("rpsDoneButton"),
            ) { Text("Back to lobby", fontFamily = FontFamily.Monospace) }
            return@Column
        }

        // Reveal area: shows pixel sprites for each side; opponent's is masked until resolved.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            ChoiceColumn(label = "YOU", choice = myChoice, masked = false)
            Text(
                "VS",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp),
            )
            ChoiceColumn(
                label = (opponent?.displayName ?: "?").uppercase(),
                choice = theirChoice,
                masked = !resolved && theirChoiceRaw != null,
            )
        }

        if (resolved) {
            val msg = roundOutcomeMessage(myChoice, theirChoiceRaw)
            Text(
                msg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "Next round starting…",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else if (myChoice == null) {
            Text(
                "Pick your move",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ChoicePicker(onPick = onPlay)
        } else {
            Text(
                "Locked in. Waiting on opponent…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun roundOutcomeMessage(myChoice: String?, theirChoice: String?): String {
    if (myChoice == null || theirChoice == null) return ""
    if (myChoice == theirChoice) return "🤝 TIE"
    val youWin = (myChoice == "rock" && theirChoice == "scissors") ||
        (myChoice == "paper" && theirChoice == "rock") ||
        (myChoice == "scissors" && theirChoice == "paper")
    return if (youWin) "✨ YOU WIN THIS ROUND" else "💥 OPPONENT WINS"
}

// ─── SOLO GAME ───────────────────────────────────────────────────────────────

@Composable
private fun RpsSoloGame(onExit: () -> Unit) {
    var myScore by remember { mutableStateOf(0) }
    var cpuScore by remember { mutableStateOf(0) }
    var roundNumber by remember { mutableStateOf(1) }
    var myChoice by remember { mutableStateOf<String?>(null) }
    var cpuChoice by remember { mutableStateOf<String?>(null) }
    var revealing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(myChoice) {
        val mine = myChoice ?: return@LaunchedEffect
        revealing = true
        delay(900)
        val cpu = listOf("rock", "paper", "scissors").random()
        cpuChoice = cpu
        delay(900)
        when {
            mine == cpu -> Unit
            (mine == "rock" && cpu == "scissors") ||
                (mine == "paper" && cpu == "rock") ||
                (mine == "scissors" && cpu == "paper") -> myScore += 1
            else -> cpuScore += 1
        }
        delay(900)
        if (myScore >= 2 || cpuScore >= 2) {
            finished = true
        } else {
            roundNumber += 1
            myChoice = null
            cpuChoice = null
            revealing = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Scoreboard(
            meName = "YOU",
            theirName = "CPU",
            meScore = myScore,
            theirScore = cpuScore,
            roundNumber = roundNumber,
        )

        if (finished) {
            Text(
                if (myScore >= 2) "🏆 YOU BEAT THE CPU" else "💀 CPU WINS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().testTag("rpsSoloDone"),
            ) { Text("Back", fontFamily = FontFamily.Monospace) }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ChoiceColumn(label = "YOU", choice = myChoice, masked = false)
            Text(
                "VS",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 32.dp),
            )
            ChoiceColumn(
                label = "CPU",
                choice = cpuChoice,
                masked = revealing && cpuChoice == null,
            )
        }

        if (myChoice != null && cpuChoice != null) {
            Text(
                roundOutcomeMessage(myChoice, cpuChoice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else if (myChoice == null) {
            Text(
                "Pick your move",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ChoicePicker(onPick = { myChoice = it })
        } else {
            Text(
                "CPU is choosing…",
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ─── SHARED UI ───────────────────────────────────────────────────────────────

@Composable
private fun Scoreboard(
    meName: String,
    theirName: String,
    meScore: Int,
    theirScore: Int,
    roundNumber: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ROUND $roundNumber  ·  BEST OF 3",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(meName.uppercase(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(
                        meScore.toString(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "—",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(theirName.uppercase(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(
                        theirScore.toString(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceColumn(label: String, choice: String?, masked: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(8.dp),
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                masked -> Text("?", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineLarge)
                choice == "rock" -> PixelSprite(grid = ROCK_GRID, palette = ROCK_PALETTE, pixelSize = 6.dp)
                choice == "paper" -> PixelSprite(grid = PAPER_GRID, palette = PAPER_PALETTE, pixelSize = 6.dp)
                choice == "scissors" -> PixelSprite(grid = SCISSORS_GRID, palette = SCISSORS_PALETTE, pixelSize = 6.dp)
                else -> Text("…", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineLarge)
            }
        }
    }
}

@Composable
private fun ChoicePicker(onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChoiceButton("rock", ROCK_GRID, ROCK_PALETTE, onPick, "rpsPick:rock")
        ChoiceButton("paper", PAPER_GRID, PAPER_PALETTE, onPick, "rpsPick:paper")
        ChoiceButton("scissors", SCISSORS_GRID, SCISSORS_PALETTE, onPick, "rpsPick:scissors")
    }
}

@Composable
private fun ChoiceButton(
    choice: String,
    grid: List<String>,
    palette: Map<Char, Color>,
    onPick: (String) -> Unit,
    tag: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onPick(choice) }
            .padding(8.dp)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(8.dp),
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            PixelSprite(grid = grid, palette = palette, pixelSize = 5.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            choice.uppercase(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── PIXEL ART ───────────────────────────────────────────────────────────────

@Composable
private fun PixelSprite(
    grid: List<String>,
    palette: Map<Char, Color>,
    pixelSize: Dp,
) {
    val density = LocalDensity.current
    val px = with(density) { pixelSize.toPx() }
    val rows = grid.size
    val cols = grid.firstOrNull()?.length ?: 0
    Canvas(
        modifier = Modifier.size(width = pixelSize * cols, height = pixelSize * rows),
    ) {
        for (y in 0 until rows) {
            val row = grid[y]
            for (x in 0 until row.length) {
                val color = palette[row[x]] ?: continue
                drawRect(
                    color = color,
                    topLeft = Offset(x * px, y * px),
                    size = Size(px, px),
                )
            }
        }
    }
}

private val ROCK_PALETTE = mapOf(
    '.' to Color.Transparent,
    '#' to Color(0xFF757575),
    '*' to Color(0xFF424242),
    'o' to Color(0xFFBDBDBD),
)
private val ROCK_GRID = listOf(
    "............",
    "....****....",
    "...*####*...",
    "..*#oooo#*..",
    ".*#oo###o#*.",
    "*#ooo###oo#*",
    "*#ooo####o#*",
    "*#oo#####o#*",
    ".*#oo###o#*.",
    "..*#oooo#*..",
    "...******...",
    "............",
)

private val PAPER_PALETTE = mapOf(
    '.' to Color.Transparent,
    '#' to Color(0xFFFFFDE7),
    '*' to Color(0xFF9E9E9E),
    'o' to Color(0xFFBDBDBD),
)
private val PAPER_GRID = listOf(
    "............",
    ".**********.",
    ".*########*.",
    ".*#oooooo#*.",
    ".*########*.",
    ".*#oooooo#*.",
    ".*########*.",
    ".*#oooooo#*.",
    ".*########*.",
    ".*#oooooo#*.",
    ".**********.",
    "............",
)

private val SCISSORS_PALETTE = mapOf(
    '.' to Color.Transparent,
    '#' to Color(0xFFB0BEC5),
    'o' to Color(0xFFD4A054),
)
private val SCISSORS_GRID = listOf(
    "##........##",
    ".##......##.",
    "..##....##..",
    "...##..##...",
    "....####....",
    "...##..##...",
    "...##..##...",
    ".ooo....ooo.",
    "o..o....o..o",
    "o..o....o..o",
    ".ooo....ooo.",
    "............",
)
