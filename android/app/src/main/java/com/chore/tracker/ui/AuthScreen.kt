package com.chore.tracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.chore.tracker.R
import com.chore.tracker.data.Repo
import kotlinx.coroutines.launch

private enum class Mode { Login, NewHousehold, JoinHousehold }

@Composable
fun AuthScreen(repo: Repo, onSignedIn: () -> Unit) {
    var mode by remember { mutableStateOf(Mode.Login) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("authScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_sock),
            contentDescription = "Dobby",
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("Dobby", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            when (mode) {
                Mode.Login -> "Sign in"
                Mode.NewHousehold -> "Create account"
                Mode.JoinHousehold -> "Join household"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            email,
            { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth().testTag("emailField"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("passwordVisibilityToggle"),
                ) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("passwordField"),
        )

        if (mode != Mode.Login) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                displayName,
                { displayName = it },
                label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth().testTag("displayNameField"),
            )
        }
        if (mode == Mode.NewHousehold) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                householdName,
                { householdName = it },
                label = { Text("Household name") },
                modifier = Modifier.fillMaxWidth().testTag("householdNameField"),
            )
        }
        if (mode == Mode.JoinHousehold) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                inviteCode,
                { inviteCode = it },
                label = { Text("Invite code") },
                modifier = Modifier.fillMaxWidth().testTag("inviteCodeField"),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, modifier = Modifier.testTag("authError"))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("submitButton"),
            onClick = {
                error = null
                busy = true
                scope.launch {
                    runCatching {
                        when (mode) {
                            Mode.Login -> repo.login(email.trim(), password)
                            Mode.NewHousehold -> repo.register(
                                email.trim(),
                                password,
                                displayName.trim(),
                                householdName = householdName.trim(),
                            )
                            Mode.JoinHousehold -> repo.register(
                                email.trim(),
                                password,
                                displayName.trim(),
                                inviteCode = inviteCode.trim(),
                            )
                        }
                    }
                        .onSuccess { onSignedIn() }
                        .onFailure { error = it.message ?: "failed" }
                    busy = false
                }
            },
        ) {
            Text(
                when (mode) {
                    Mode.Login -> "Sign in"
                    Mode.NewHousehold -> "Create"
                    Mode.JoinHousehold -> "Join"
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = {
                    mode = if (mode == Mode.Login) Mode.NewHousehold else Mode.Login
                },
            ) {
                Text(if (mode == Mode.Login) "New household" else "Have an account?")
            }
            TextButton(onClick = { mode = Mode.JoinHousehold }) { Text("Join with code") }
        }
    }
}
