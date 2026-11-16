package com.example.test_tokenbucket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_tokenbucket.ui.theme.Test_tokenbucketTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background){
                    RateLimiterSimulatorScreen()
                }
            }
        }
    }
}

data class GatewayResponse(
    val timestamp: String,
    val statusCode: Int,
    val detailMessage: String
)
class TokenBucketLimiter(
    private val bucketCapacity: Int = 4,
    private val refillRatePerSecond: Double = 1.0
) {
    val name = "Token Bucket (Xô chứa token)"
    val description = "Dung lượng xô: $bucketCapacity token | Tốc độ nạp: $refillRatePerSecond token/giây"

    private data class Bucket(var tokens: Double, var lastRefillTimeMs: Long)
    private val buckets = mutableMapOf<String, Bucket>()

    @Synchronized
    fun allowRequest(clientId: String): Pair<Boolean, String> {
        val now = System.currentTimeMillis()
        val bucket = buckets.getOrPut(clientId) { Bucket(bucketCapacity.toDouble(), now) }
        // Tính lượng token được tự động nạp thêm theo thời gian trôi qua
        val elapsedSeconds = (now - bucket.lastRefillTimeMs) / 1000.0
        if (elapsedSeconds > 0) {
            val newTokens = elapsedSeconds * refillRatePerSecond
            // Khi xô đầy, token dư thừa bị tràn ra ngoài (giới hạn bởi bucketCapacity)
            bucket.tokens = min(bucketCapacity.toDouble(), bucket.tokens + newTokens)
            bucket.lastRefillTimeMs = now
        }
        return if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0
            Pair(true, "Còn lại: ${"%.2f".format(bucket.tokens)}/$bucketCapacity token")
        } else {
            Pair(false, "Hết token (${"%.2f".format(bucket.tokens)} token) -> Chặn!")
        }
    }

    @Synchronized
    fun reset() = buckets.clear()
}
@Composable
fun RateLimiterSimulatorScreen() {
    val tokenBucket = remember {
        TokenBucketLimiter(bucketCapacity = 4, refillRatePerSecond = 1.0)
    }
    val requestLogs = remember { mutableStateListOf<GatewayResponse>() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val clientId = "Client_IP_192.168.1.1"
    fun sendMockRequest() {
        val (allowed, detail) = tokenBucket.allowRequest(clientId)
        val status = if (allowed) 200 else 429
        requestLogs.add(
            index = 0, // Đẩy log mới nhất lên đầu danh sách
            element = GatewayResponse(
                timestamp = timeFormat.format(Date()),
                statusCode = status,
                detailMessage = detail
            )
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "API GATEWAY - TOKEN BUCKET",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64B5F6)
        )
        Text(
            text = "Mô phỏng kiểm soát lưu lượng & chống DoS",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = tokenBucket.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tokenBucket.description,
                    fontSize = 13.sp,
                    color = Color(0xFFA6E3A1)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { sendMockRequest() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Gửi 1 Request", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    repeat(6) { sendMockRequest() }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text("Burst (x6 Req)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    tokenBucket.reset()
                    requestLogs.clear()
                }
            ) {
                Text("Reset")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nhật ký phản hồi từ API Gateway (${requestLogs.size}):",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.LightGray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Danh sách hiển thị kết quả HTTP 200 vs HTTP 429
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(requestLogs) { log ->
                val isAllowed = log.statusCode == 200
                val bgColor = if (isAllowed) Color(0xFF1B3B2B) else Color(0xFF3E1F24)
                val badgeColor = if (isAllowed) Color(0xFF4CAF50) else Color(0xFFFF5252)
                val statusText = if (isAllowed) "HTTP 200 OK" else "HTTP 429 Too Many Requests"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = statusText,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "[${log.timestamp}]",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.detailMessage,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Test_tokenbucketTheme {
        RateLimiterSimulatorScreen()
    }
}