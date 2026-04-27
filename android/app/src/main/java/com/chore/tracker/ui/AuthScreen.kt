package com.chore.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("authScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            when (mode) {
                Mode.Login -> "Sign in"
                Mode.NewHousehold -> "Create account"
                Mode.JoinHousehold -> "Join household"
            },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            email,
            { email = it },
            label = { Text("Email") },
            modifier = Modifier.testTag("emailField"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.testTag("passwordField"),
        )

        if (mode != Mode.Login) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                displayName,
                { displayName = it },
                label = { Text("Your name") },
                modifier = Modifier.testTag("displayNameField"),
            )
        }
        if (mode == Mode.NewHousehold) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                householdName,
                { householdName = it },
                label = { Text("Household name") },
                modifier = Modifier.testTag("householdNameField"),
            )
        }
        if (mode == Mode.JoinHousehold) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                inviteCode,
                { inviteCode = it },
                label = { Text("Invite code") },
                modifier = Modifier.testTag("inviteCodeField"),
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
