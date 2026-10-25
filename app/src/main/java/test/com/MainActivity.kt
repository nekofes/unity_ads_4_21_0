package test.com

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import test.com.databinding.ActivityMainBinding

//ViewCompat.setOnApplyWindowInsetsListenerのため使用しています
import androidx.core.view.ViewCompat
//4.21.0におけるインポート文です
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsError
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.BannerAd
import com.unity3d.ads.BannerConfiguration //最新SDKでは「Load」がつかないこちらに統一されています
import com.unity3d.ads.BannerShowListener
import com.unity3d.ads.BannerSize
import com.unity3d.ads.LoadListener

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.*

//デリゲート（委譲）用のインターフェースを定義
interface OnScreenSizeCalculatedListener
{
    fun onSizeCalculated()
}
enum class UnityAdsDisplay(val value:Int)
{
    NONE(0),
    DISPLAY(1)
}
//イベントの結果に使用するステータス
enum class UnityAdsStatus(val value: Int)
{
    RESULT_OK(0),
    INITIALIZATION_FAILED(1),
    INITIALIZATION_FAILED_FIRST_TIME(2),
    BANNER_LOADED(3),
    BANNER_SHOWN(4),
    BANNER_LOAD_FAILED(5)

}

class MainActivity : AppCompatActivity(), OnScreenSizeCalculatedListener
{

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var unityAdsDisplay: UnityAdsDisplay = UnityAdsDisplay.NONE
    private var screenWidth : Int = 0
    private var screenHeight : Int = 0
    private var statusBarHeight : Int = 0
    private var navigationBarHeight : Int = 0
    private var adHeight: Float=0F
    // アクティビティのプロパティ（メンバー変数）として定義
    private var isInitializing = false
    private var initState:Int=0
    // 画面側（MainActivityなど）のクラスの上のほう（メンバー変数）にフラグを用意
    private var isSizeCalculated = false

    // 1. クラスの最上部などで、リスナーの型を定義しておく
    private var unityAdsDisplayListener: ((UnityAdsStatus) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adHeight = adHeight() // 50dpをピクセル化

        //OSのサイズ確定タイミングを捉えます。
        //サイズ確定を待つ処理です。
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            if (!isSizeCalculated)
            {
                val screenSize = screenSize()
                screenWidth = screenSize.first
                screenHeight = screenSize.second

                val controlSize = screenUsingBar()
                statusBarHeight = controlSize.first
                navigationBarHeight = controlSize.second

                //計算が終わったらフラグを true にしてロックします
                isSizeCalculated = true

                //デリゲートメソッドを呼び出して、実際の処理を委譲します。
                onSizeCalculated()

            }
            windowInsets
        }
    }
    private fun screenSize() : Pair<Int, Int>
    {
        var width: Int = 0
        var height: Int = 0

        if (Build.VERSION.SDK_INT < 30) //API 29以下（Android 10以下）
        {
            //従来の取得方法
            width = this.resources.displayMetrics.widthPixels
            height = this.resources.displayMetrics.heightPixels
        }
        else //API 30以上（Android 11以上）
        {
            //近年のAndroidで推奨されている新しい統一パターン
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        }

        return Pair(width, height)
    }
    /*
    画面の開始位置を計算するため
    ステータスバーやナビゲーションバーの高さを返す処理
    */
    private fun screenUsingBar() : Pair<Int, Int>
    {
        var statusBarHeight : Int = 0
        var navigationBarHeight : Int = 0
        if(Build.VERSION.SDK_INT >= 30)
        {
            statusBarHeight = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars()).top
            navigationBarHeight = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars()).bottom
        }
        else
        {
            var resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight =
                if (resourceId > 0)
                {
                    resources.getDimensionPixelSize(resourceId)
                }
                else
                {
                    0
                }
            resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            navigationBarHeight =
                if (resourceId > 0)
                {
                    resources.getDimensionPixelSize(resourceId)
                }
                else
                {
                    0
                }
        }

        return Pair(statusBarHeight, navigationBarHeight)
    }

    //デリゲートされた処理をここに集約します。
    override fun onSizeCalculated()
    {
        control()
        initUnityAdsWithRetry()
    }
    //バナー広告の高さ
    private fun adHeight(): Float
    {
        //画面の密度（倍率）を取得
        val density = resources.displayMetrics.density
        //dpからpx（ピクセル）への変換と返却
        return 50 * density
    }
    private fun initUnityAdsWithRetry()
    {
        val result=adTestMethod()
        val gameId = result.first //game id
        val testMode = result.second //デバッグ中ならtrue

        //設定オブジェクトの作成 (Bは大文字、ゲームIDはBuilderの引数に直接渡します)
        val config = InitializationConfiguration.Builder(gameId)
            .withTestMode(testMode) // テスト時はtrue、本番リリース時はfalse
            .build()

        //リスナーを明示的なオブジェクトとして定義 (引数は UnityAdsError? になります)
        val listener = object : InitializationListener {
            override fun onInitializationComplete(error: UnityAdsError?) {
                if (error == null) {
                    Log.d("UnityAds", "初期化に成功しました！")
                    // 初期化が成功したことが確定したので、バナーを読み込みます
                    loadUnityBanner()
                }
                else
                {
                    //エラーの内容を文字列として出力します
                    Log.e("UnityAds", "初期化に失敗しました: ${error.message}")

                    //旧コードの引数「message」の代わりに「error.message」を使用します
                    val failMessage = error.message ?: "Unknown Error"

                    if (initState == 0) {
                        initState = 1
                        unityAdsDisplayListener?.invoke(UnityAdsStatus.INITIALIZATION_FAILED_FIRST_TIME)
                    }
                    isInitializing = false
                    unityAdsDisplay = UnityAdsDisplay.NONE // 広告非表示のため
                    binding.adArea.visibility = View.VISIBLE
                    Log.e("UnityAds", "初期化失敗: $failMessage")

                    unityAdsDisplayListener?.invoke(UnityAdsStatus.INITIALIZATION_FAILED)

                    //10秒後に自動で再初期化を試みます（コルーチン）
                    //import androidx.lifecycle.lifecycleScope
                    lifecycleScope.launch {
                        Log.e("UnityAds", "delay(10000)実行前")
                        delay(10000)
                        initUnityAdsWithRetry()
                    }
                }
            }
        }
        //初期化の実行
        UnityAds.initialize(config, listener)

        //unityAdsViewのデリゲートを受け取るイベントを実装します
        //イベントの実装（binding.adView ではなく、直接変数に代入します）
        unityAdsDisplayListener = { value ->
            when (value) {
                UnityAdsStatus.INITIALIZATION_FAILED -> { /* 失敗時の処理 */ }
                UnityAdsStatus.BANNER_LOADED -> { /* ロード完了時の処理 */ }
                else -> { /* それ以外の処理 */ }
            }
        }
    }
    private fun loadUnityBanner() {
        val placementId = placementId()

        // 1. 重複防止のため、まずは器（adView）の中身を綺麗にクリア
        binding.adView.removeAllViews()

        // 2. 表示（Shown）を監視するリスナー
        val showListener = object : BannerShowListener {
            override fun onImpression(banner: BannerAd) {
                runOnUiThread {
                    Log.d("UnityAds", "バナーが表示されました（インプレッション）。")
                    unityAdsDisplay = UnityAdsDisplay.DISPLAY
                    unityAdsDisplayListener?.invoke(UnityAdsStatus.BANNER_SHOWN)
                    // 広告が出たらラベルは完全に非表示
                    binding.adArea.visibility = android.view.View.INVISIBLE
                    binding.adView.visibility = android.view.View.VISIBLE
                }
            }
            override fun onFailedToShow(banner: BannerAd, error: UnityAdsError) {
                runOnUiThread {
                    Log.e("UnityAds", "バナーの表示に失敗しました: ${error.message}")
                    unityAdsDisplay = UnityAdsDisplay.NONE
                    binding.adArea.visibility = android.view.View.VISIBLE
                }
            }
            override fun onClicked(banner: BannerAd) {
                Log.d("UnityAds", "バナーがクリックされました。")
            }
        }

        // 3. 最新のBuilderを使って、サイズ、配置ID、そしてshowListenerをセットしてビルド
        val bannerSize = BannerSize(320, 50)
        val config = BannerConfiguration.Builder(placementId, bannerSize, showListener).build()
        Log.d("UnityAds", "広告のロード（読み込み）を開始します...")

        // 4. 最新の load 処理（引数は config と listener の2つのみ）
        BannerAd.load(config, object : LoadListener<BannerAd> {

            // ロード成功時の新しいメソッド名
            override fun onAdLoaded(banner: BannerAd?, error: UnityAdsError?) {
                // エラーがなく、かつ banner が無事に取得できている場合
                if (error == null && banner != null) {
                    Log.d("UnityAds", "★バナーのロードに成功しました。")

                    runOnUiThread {
                        // 過去の失敗時の黄色背景をクリア
                        binding.adView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        unityAdsDisplay = UnityAdsDisplay.DISPLAY
                        unityAdsDisplayListener?.invoke(UnityAdsStatus.BANNER_LOADED)

                        // 320dp相当のピクセル幅を計算
                        val density = resources.displayMetrics.density
                        val adWidthPx = (320 * density).toInt()  // 約960px
                        val adHeightPx = adHeight.toInt()        // 50dp相当

                        //下記の指定方法は方法1もしくは方法2のいずれでも実行可能でした
                        //[方法1]
                        /*
                        // 親パーツの型「CoordinatorLayout.LayoutParams」に書き換えます
                        val params = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                            adWidthPx,
                            adHeightPx
                        ).apply {
                            // 中央寄せの設定方法も親のルールに合わせます
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                        }

                        binding.adView.layoutParams = params
                        */

                        //[方法2]
                        binding.adView.layoutParams.apply {
                            width = adWidthPx
                            height = adHeightPx
                            //もし既存のLayoutParamsにgravityがあれば設定を上書き（なければこの行は不要またはエラーになります。エラーが出たらこの下の1行を消してください）
                            (this as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        }

                        //5. 取得した広告のViewを、直接binding.adViewに流し込む（公式の新しい推奨）
                        // ※これにより ad.view の null バグを完全に回避できます
                        //val adView = banner.getView()
                        val adView = banner.view
                        binding.adView.addView(adView)

                    }
                } else {
                    // エラーがあった場合は、下の失敗時処理と同じ動きをさせる
                    Log.e("UnityAds", "❌バナーのロードに失敗しました。理由: ${error?.message}")
                    handleLoadFailure()
                }
            }
        })
    }

    // 失敗時の処理をまとめたヘルパー関数
    private fun handleLoadFailure() {
        runOnUiThread {
            unityAdsDisplay = UnityAdsDisplay.NONE
            binding.adArea.visibility = android.view.View.VISIBLE
            unityAdsDisplayListener?.invoke(UnityAdsStatus.BANNER_LOAD_FAILED)

            // 失敗時は枠を維持して黄色にする
            binding.adView.layoutParams.apply {
                height = adHeight.toInt()
            }
            binding.adView.setBackgroundColor(android.graphics.Color.argb(100, 255, 255, 100))
        }
    }

    private fun adTestMethod() : Pair<String, Boolean>
    {
        //testMethod()の戻り値
        //0:本番環境
        //1:テスト環境
        val result:Int=testMethod()
        //123456789:あなたのUnity Game ID
        val gameId = if(result==0)"123456789" else "4550882"
        val isTestMode = if(result==0)false else true // デバッグ中ならtrue（テストモードON）

        return Pair(gameId, isTestMode)
    }
    private fun placementId() : String
    {
        val result:Int = testMethod()
        //xxx:Unity登録画面で取得したIDを登録
        return if(result==0) "xxx" else "Banner_Android"
    }
    /*
    BuildConfig.DEBUGの実行結果と同等の処理
    デバッグ中ならtrue（テストモードON）
    testMethod()の戻り値
    0:本番環境
    1:テスト環境
    */
    private fun testMethod(): Int
    {
        // 1. 【バックアップ手段】Androidシステム全体のログ設定からデバッグ状態を推測します。
        // コマンド（adb shell setprop log.tag.MYSIGN DEBUG）等で開発者が制御できる裏ルートです。
        val isDebug = (android.os.Build.VERSION.SDK_INT >= 0) &&
                      (android.util.Log.isLoggable("MYSIGN", android.util.Log.DEBUG))

        // 2. 【メインの手段】クラスの存在チェック
        /*
        2. もしくは、より確実でエラーが絶対に出ない「クラスの存在チェック」を使います。
        あなたのアプリのパッケージ名にあわせてください。
        大元のパッケージ名が test.com の場合、自動生成されるクラスのフルネームは test.com.BuildConfig になります。
        */
        val hasDebug = try
        {
            val packageName = this.packageName
            Class.forName("$packageName.BuildConfig")
                .getField("DEBUG")
                .getBoolean(null)
        }
        catch (e: Exception)
        {
            false
        }

        //メイン(hasDebug)がダメでも、バックアップ(isDebug)が true ならテスト環境(1)とみなします。
        return if (hasDebug || isDebug)
        {
            1
        }
        else
        {
            0
        }
    }
    //画面コントロール
    private fun control()
    {
        var y = 0F

        y = statusBarHeight.toFloat()

        binding.adArea.x = 0F
        binding.adArea.y = y
        binding.adArea.layoutParams.width = screenWidth
        binding.adArea.layoutParams.height = adHeight.toInt()

        binding.adArea.typeface = Typeface.DEFAULT_BOLD
        binding.adArea.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        binding.adArea.setBackgroundColor(Color.argb(240,102,153,255))
        binding.adArea.setTextColor(Color.WHITE)
        binding.adArea.textSize=16F
        binding.adArea.text = "広告が表示されるスペース"

        //計算で位置決めしているため広告を表示するエリアの位置決め座標も同様に計算配置します。
        binding.adView.x = 0F
        binding.adView.y = y


    }

    //画面が閉じるときに自動で呼ばれる後片付け（追加）
    override fun onDestroy()
    {
        //Unity Adsのバナーを完全に破棄して通信を止めます。
        //最後に親（OS）の終了処理を呼びます（これは必須）。
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId)
        {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean
    {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}