package com.hoon.photosorter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var granted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)

        setContent {
            SorterTheme {
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    granted = hasMediaPermission()
                    if (granted) vm.reload()
                }
                // 안드로이드 시스템의 삭제 확인 창 결과
                val trashLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) { r -> if (r.resultCode == RESULT_OK) vm.onTrashed() }

                Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
                    if (!granted) {
                        PermissionScreen(
                            onRequest = { permLauncher.launch(mediaPermissions()) },
                            onSettings = ::openAppSettings,
                        )
                    } else {
                        when (val s = vm.screen) {
                            Screen.Home -> HomeScreen(vm)
                            is Screen.Sort -> SortScreen(vm, s.date, onPlay = ::playVideo)
                            is Screen.Compare -> CompareScreen(vm, s.groupId, s.from)
                            Screen.Review -> ReviewScreen(vm, useTrash = Build.VERSION.SDK_INT >= 30) {
                                if (Build.VERSION.SDK_INT >= 30) {
                                    val uris = vm.startTrash()
                                    if (uris.isNotEmpty()) {
                                        val pi = MediaStore.createTrashRequest(contentResolver, uris, true)
                                        trashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                    }
                                } else {
                                    // 안드로이드 10 이하는 시스템 휴지통이 없어 바로 지운다
                                    vm.deletePermanently()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 다른 앱에서 사진을 찍거나 지웠을 수 있으니 돌아올 때마다 다시 읽는다
        granted = hasMediaPermission()
        if (granted) vm.reload()
    }

    private fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun hasMediaPermission(): Boolean {
        fun ok(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                ok(Manifest.permission.READ_MEDIA_IMAGES) || ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> ok(Manifest.permission.READ_MEDIA_IMAGES)
            else -> ok(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun playVideo(item: MediaItem) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(item.uri, "video/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("사진과 동영상을 보려면 접근 권한이 필요해요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "‘모두 허용’을 골라야 전체 사진을 정리할 수 있어요. 이 앱은 인터넷을 쓰지 않고 휴대폰 안에서만 동작해요.",
            color = Muted, fontSize = 15.sp,
        )
        BigButton("권한 허용하기", Keep, OnKeep, Modifier.fillMaxWidth(), onClick = onRequest)
        TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("허용 창이 안 뜨면 설정에서 직접 허용하기", color = TextSub)
        }
    }
}
