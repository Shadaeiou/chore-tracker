package com.chore.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chore.tracker.data.Repo
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(repo: Repo, onSignedIn: () -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var householdName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (isRegister) "Create account" else "Sign in")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
        )
        if (isRegister) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Your name") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(householdName, { householdName = it }, label = { Text("Household name") })
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy,
            onClick = {
                error = null
                busy = true
                scope.launch {
                    runCatching {
                        if (isRegister)
                            repo.register(email.trim(), password, displayName.trim(), householdName.trim())
                        else
                            repo.login(email.trim(), password)
                    }
                        .onSuccess { onSignedIn() }
                        .onFailure { error = it.message ?: "failed" }
                    busy = false
                }
            },
        ) { Text(if (isRegister) "Register" else "Sign in") }
        TextButton(onClick = { isRegister = !isRegister }) {
            Text(if (isRegister) "Have an account? Sign in" else "New here? Create account")
        }
    }
}
