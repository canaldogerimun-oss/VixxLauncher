package com.movtery.zalithlauncher.ui.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.view.Skin3DPreviewView
import com.movtery.zalithlauncher.feature.accounts.AccountUtils
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.value.MinecraftAccount
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class VixxSkinStudioFragment : FragmentWithAnim(R.layout.fragment_vixx_skin_studio) {
    companion object { const val TAG = "VixxSkinStudioFragment" }

    private lateinit var preview: Skin3DPreviewView
    private lateinit var nameView: TextView
    private lateinit var modelView: TextView
    private lateinit var collection: LinearLayout
    private lateinit var emptyView: TextView
    private var selectedFile: File? = null
    private var selectedSlim = false
    private var selectedBitmap: Bitmap? = null
    private val skinDir by lazy { File(requireContext().filesDir, "vixx_skins").apply { mkdirs() } }
    private val prefs by lazy { requireContext().getSharedPreferences("vixx_skin_studio", 0) }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSkin(uri)
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) exportSkin(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        preview = view.findViewById(R.id.skin3dPreview)
        nameView = view.findViewById(R.id.skinName)
        modelView = view.findViewById(R.id.skinModel)
        collection = view.findViewById(R.id.skinCollection)
        emptyView = view.findViewById(R.id.collectionEmpty)

        view.findViewById<View>(R.id.importSkinButton).setOnClickListener {
            importLauncher.launch(arrayOf("image/png"))
        }
        view.findViewById<View>(R.id.exportSkinButton).setOnClickListener {
            val file = selectedFile
            if (file == null) toast("Selecione uma skin primeiro.")
            else exportLauncher.launch(displayName(file).replace(Regex("[^A-Za-z0-9_-]"), "_") + if (selectedSlim) "-slim.png" else "-classic.png")
        }
        view.findViewById<View>(R.id.modelClassicButton).setOnClickListener { setModel(false) }
        view.findViewById<View>(R.id.modelSlimButton).setOnClickListener { setModel(true) }
        view.findViewById<View>(R.id.viewFront).setOnClickListener { preview.setViewPreset("front") }
        view.findViewById<View>(R.id.viewBack).setOnClickListener { preview.setViewPreset("back") }
        view.findViewById<View>(R.id.viewLeft).setOnClickListener { preview.setViewPreset("left") }
        view.findViewById<View>(R.id.viewRight).setOnClickListener { preview.setViewPreset("right") }
        view.findViewById<View>(R.id.viewTop).setOnClickListener { preview.setViewPreset("top") }
        view.findViewById<View>(R.id.viewFree).setOnClickListener { preview.setViewPreset("free") }
        var outerVisible = true
        view.findViewById<TextView>(R.id.toggleOuterLayer).setOnClickListener {
            outerVisible = !outerVisible
            preview.setOuterLayerVisible(outerVisible)
            it as TextView
            it.text = if (outerVisible) "Camada externa: visível" else "Camada externa: oculta"
        }
        view.findViewById<View>(R.id.openSkinEditorButton).setOnClickListener {
            val file = selectedFile
            if (file == null) toast("Selecione uma skin primeiro.")
            else ZHTools.swapFragmentWithAnim(this, VixxSkinEditorFragment::class.java, VixxSkinEditorFragment.TAG, null)
        }
        view.findViewById<View>(R.id.applySkinButton).setOnClickListener { applyToMinecraftAccount() }
        view.findViewById<View>(R.id.skinStudioHelpButton).setOnClickListener { showSkinStudioHelp() }

        // Seed local templates on first use; no network request is needed.
        ensureDefaultSkins()
        refreshCollection()
        val last = prefs.getString("selected_skin", null)?.let(::File)
        val initial = last?.takeIf { it.isFile } ?: File(skinDir, "Steve_Classic.png")
        selectSkin(initial)
    }

    private fun showSkinStudioHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("Como funciona o Skin Studio")
            .setMessage(
                "1. Escolha Steve (Classic) ou Alex (Slim), ou importe uma textura PNG 64×64.\n\n" +
                "2. Arraste o personagem para girar. Use dois dedos para aproximar ou afastar. Os botões Frente, Costas e laterais mudam a câmera.\n\n" +
                "3. Toque em Abrir editor avançado para pintar a textura. O Minecraft usa uma imagem 64×64: a parte principal define corpo, cabeça, braços e pernas; a camada externa cria chapéu, jaqueta, mangas e calças. Pixels transparentes devem ser preservados.\n\n" +
                "4. Salve no editor e volte à prévia. Exporte PNG para guardar ou compartilhar sua skin.\n\n" +
                "5. Em uma conta Microsoft, use Aplicar na conta Minecraft para publicar no perfil oficial (é necessária conexão e autenticação válida). Em contas locais/offline, o VIXX guarda a textura no dispositivo; isso, por si só, não altera o visual do cliente Java nem faz outros jogadores vê-la. A exibição em servidores depende do suporte do servidor."
            )
            .setPositiveButton("Entendi", null)
            .show()
    }

    override fun onPause() {
        if (::preview.isInitialized) preview.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) preview.onResume()
    }

    override fun onDestroyView() {
        selectedBitmap?.recycle()
        selectedBitmap = null
        super.onDestroyView()
    }

    private fun ensureDefaultSkins() {
        try {
            val steve = File(skinDir, "Steve_Classic.png")
            if (!steve.exists()) {
                requireContext().assets.open("steve.png").use { input ->
                    steve.outputStream().use { output -> input.copyTo(output) }
                }
                prefs.edit().putString(nameKey(steve), "Steve • Padrão")
                    .putBoolean(modelKey(steve), false).apply()
            }

            val alex = File(skinDir, "Alex_Slim.png")
            if (!alex.exists()) {
                requireContext().assets.open("alex.png").use { input ->
                    alex.outputStream().use { output -> input.copyTo(output) }
                }
                prefs.edit().putString(nameKey(alex), "Alex • Modelo Slim")
                    .putBoolean(modelKey(alex), true).apply()
            }

            val blank = File(skinDir, "Skin_Em_Branco.png")
            if (!blank.exists()) {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val white = Paint().apply { color = Color.WHITE }
                // Opaque white pixels only in the six base-layer UV regions.
                // The second/outer layer remains transparent for immediate editing.
                listOf(
                    android.graphics.Rect(0, 0, 32, 16),    // head
                    android.graphics.Rect(16, 16, 40, 32),  // torso
                    android.graphics.Rect(40, 16, 56, 32),  // right arm
                    android.graphics.Rect(0, 16, 16, 32),   // right leg
                    android.graphics.Rect(32, 32, 56, 48), // left arm
                    android.graphics.Rect(16, 48, 32, 64)  // left leg
                ).forEach { canvas.drawRect(it, white) }
                FileOutputStream(blank).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                bitmap.recycle()
                prefs.edit().putString(nameKey(blank), "Skin em branco")
                    .putBoolean(modelKey(blank), false).apply()
            }
        } catch (e: Exception) {
            toast("Não foi possível preparar as skins padrão: ${e.localizedMessage}")
        }
    }

    private fun importSkin(uri: Uri) {
        try {
            val source = requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Não foi possível ler o PNG.")
            if (source.width != 64 || (source.height != 64 && source.height != 32)) {
                source.recycle()
                toast("A skin precisa ter 64×64 ou 64×32 pixels.")
                return
            }
            // Legacy 64×32 skins share the same base UV area. Expanding to 64×64
            // adds a transparent outer layer while preserving every original pixel.
            val normalized = if (source.height == 32) {
                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { target ->
                    val canvas = Canvas(target)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(source, 0f, 0f, paint)
                    // Legacy skins have only the right limbs. Mirror their UV blocks
                    // into the modern left-limb slots, leaving the outer layer transparent.
                    fun mirrorBlock(src: android.graphics.Rect, dst: android.graphics.Rect) {
                        val save = canvas.save()
                        canvas.scale(-1f, 1f, (dst.left + dst.right) / 2f, (dst.top + dst.bottom) / 2f)
                        canvas.drawBitmap(source, src, dst, paint)
                        canvas.restoreToCount(save)
                    }
                    mirrorBlock(android.graphics.Rect(40, 16, 56, 32), android.graphics.Rect(40, 32, 56, 48))
                    mirrorBlock(android.graphics.Rect(0, 16, 16, 32), android.graphics.Rect(16, 48, 32, 64))
                }.also { source.recycle() }
            } else source.copy(Bitmap.Config.ARGB_8888, false).also { source.recycle() }

            val file = File(skinDir, "skin_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Falha ao salvar a skin.")
                }
            }
            normalized.recycle()
            prefs.edit().putBoolean(modelKey(file), false)
                .putString(nameKey(file), "Skin ${file.nameWithoutExtension.takeLast(6)}").apply()
            selectSkin(file)
            refreshCollection()
            toast("Skin importada para sua coleção!")
        } catch (e: Exception) {
            toast("Erro ao importar: ${e.localizedMessage ?: "arquivo inválido"}")
        }
    }

    private fun exportSkin(uri: Uri) {
        val file = selectedFile ?: return
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Não foi possível criar o arquivo.")
            toast("PNG exportado com sucesso.")
        } catch (e: Exception) {
            toast("Erro ao exportar: ${e.localizedMessage}")
        }
    }

    private fun setModel(slim: Boolean) {
        val file = selectedFile ?: run { toast("Selecione uma skin primeiro."); return }
        selectedSlim = slim
        prefs.edit().putBoolean(modelKey(file), slim).apply()
        modelView.text = if (slim) "Modelo: Alex • Slim" else "Modelo: Steve • Classic"
        selectedBitmap?.let { preview.setSkin(it, slim) }
    }

    private fun selectSkin(file: File) {
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: run {
            toast("Não foi possível abrir esta skin.")
            return
        }
        selectedBitmap?.recycle()
        selectedBitmap = bitmap
        selectedFile = file
        prefs.edit().putString("selected_skin", file.absolutePath).apply()
        selectedSlim = prefs.getBoolean(modelKey(file), false)
        nameView.text = displayName(file)
        modelView.text = if (selectedSlim) "Modelo: Alex • Slim" else "Modelo: Steve • Classic"
        preview.setSkin(bitmap, selectedSlim)
        refreshCollection()
    }

    private fun refreshCollection() {
        if (!isAdded || !::collection.isInitialized) return
        collection.removeAllViews()
        val files = skinDir.listFiles { f -> f.isFile && f.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        files.forEach { file ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.vixx_ds_surface_card)
                isClickable = true
                isFocusable = true
            }
            val thumb = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(loadFace(file))
            }
            val title = TextView(requireContext()).apply {
                text = displayName(file)
                setTextColor(Color.WHITE)
                textSize = 13f
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10); marginEnd = dp(8)
                }
            }
            val model = TextView(requireContext()).apply {
                text = if (prefs.getBoolean(modelKey(file), false)) "SLIM" else "CLASSIC"
                setTextColor(Color.rgb(190, 112, 255))
                textSize = 9f
            }
            row.addView(thumb); row.addView(title); row.addView(model)
            row.setOnClickListener { selectSkin(file) }
            row.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(file.nameWithoutExtension)
                    .setItems(arrayOf("Renomear", "Excluir", "Cancelar")) { dialog, which ->
                        when (which) {
                            0 -> {
                                val input = android.widget.EditText(requireContext()).apply {
                                    setText(displayName(file))
                                    setSingleLine(true)
                                    setPadding(dp(16), dp(10), dp(16), dp(10))
                                }
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Renomear skin")
                                    .setView(input)
                                    .setNegativeButton("Cancelar", null)
                                    .setPositiveButton("Salvar") { _, _ ->
                                        val clean = input.text.toString().trim()
                                            .replace(Regex("[^A-Za-z0-9 _-]"), "")
                                            .take(32)
                                        if (clean.isBlank()) {
                                            toast("Escolha um nome válido.")
                                        } else {
                                            val renamed = File(skinDir, clean + "_" + UUID.randomUUID().toString().take(6) + ".png")
                                            if (file.renameTo(renamed)) {
                                                val slim = prefs.getBoolean(modelKey(file), false)
                                                prefs.edit().remove(modelKey(file)).remove(nameKey(file))
                                                    .putBoolean(modelKey(renamed), slim)
                                                    .putString(nameKey(renamed), clean).apply()
                                                if (file == selectedFile) selectSkin(renamed)
                                                refreshCollection()
                                            } else toast("Não foi possível renomear.")
                                        }
                                    }.show()
                            }
                            1 -> {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Excluir skin?")
                                    .setMessage("A skin ${file.nameWithoutExtension} será removida da coleção.")
                                    .setNegativeButton("Cancelar", null)
                                    .setPositiveButton("Excluir") { _, _ ->
                                        if (file == selectedFile) clearSelection()
                                        file.delete()
                                        prefs.edit().remove(modelKey(file)).apply()
                                        refreshCollection()
                                    }.show()
                            }
                        }
                    }.show()
                true
            }
            collection.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(7) })
        }
    }

    private fun loadFace(file: File): Bitmap? {
        val source = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val size = minOf(source.width, source.height) / 8
        val face = Bitmap.createBitmap(source, size, size, size, size)
        source.recycle()
        return Bitmap.createScaledBitmap(face, dp(48), dp(48), false).also { if (it !== face) face.recycle() }
    }

    private fun clearSelection() {
        selectedBitmap?.recycle()
        selectedBitmap = null
        selectedFile = null
        nameView.text = "Nenhuma skin selecionada"
        modelView.text = "Selecione uma skin da coleção"
        preview.setSkin(null, false)
    }

    private fun applyToMinecraftAccount() {
        val file = selectedFile ?: run { toast("Selecione uma skin primeiro."); return }
        val account: MinecraftAccount? = AccountsManager.currentAccount
        if (account == null) {
            toast("Adicione e selecione uma conta Minecraft primeiro.")
            return
        }

        // Every account type gets a local account-scoped copy first. This keeps the
        // chosen texture available offline in the launcher and for its account avatar.
        val localFile = try {
            PathManager.DIR_VIXX_SKIN.mkdirs()
            File(PathManager.DIR_VIXX_SKIN, account.uniqueUUID + ".png").also {
                file.copyTo(it, overwrite = true)
            }
        } catch (t: Throwable) {
            toast("Não foi possível salvar a skin localmente: ${t.localizedMessage}")
            return
        }

        if (!AccountUtils.isMicrosoftAccount(account)) {
            val details = when {
                AccountUtils.isNoLoginRequired(account) ->
                    "Skin salva localmente para esta conta. O Minecraft Java puro não possui um serviço de perfil para contas offline, então o PNG não consegue alterar sozinho a textura dentro do jogo. Para exibi-la no personagem será necessário um mod cliente compatível com a versão instalada ou um servidor que forneça skins."
                AccountUtils.isOtherLoginAccount(account) ->
                    "Skin salva localmente para esta conta. O provedor ${account.accountType} não possui uma API universal de upload de skins. O VIXX não enviará credenciais a serviços externos nem tentará usar a API Microsoft. A exibição dentro do jogo depende de um mod cliente ou de suporte do servidor/provedor."
                else ->
                    "Skin salva localmente para esta conta. Este tipo de autenticação não oferece uma API de publicação reconhecida pelo VIXX."
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Skin salva neste dispositivo")
                .setMessage(details)
                .setPositiveButton("Entendi", null).show()
            return
        }

        val uploadVariant = if (selectedSlim) "slim" else "classic"
        val progress = AlertDialog.Builder(requireContext())
            .setMessage("Enviando skin para o perfil Minecraft...")
            .setCancelable(false).create()
        progress.show()
        Thread {
            var error: String? = null
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("variant", uploadVariant)
                    .addFormDataPart("file", "vixx-skin.png", file.asRequestBody("image/png".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url("https://api.minecraftservices.com/minecraft/profile/skins")
                    .header("Authorization", "Bearer ${account.accessToken}")
                    .post(body).build()
                OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body?.string()?.take(180)
                        throw IllegalStateException("HTTP ${response.code}: ${detail ?: response.message}")
                    }
                }
                // The local cache is retained as well; the official profile upload is
                // the authoritative publication mechanism for Microsoft accounts.
                file.copyTo(localFile, overwrite = true)
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Falha ao enviar skin."
            }
            activity?.runOnUiThread {
                if (isAdded) {
                    progress.dismiss()
                    if (error == null) toast("Skin publicada no perfil oficial e salva localmente.")
                    else toast("A skin ficou salva localmente, mas a publicação falhou: $error")
                }
            }
        }.start()
    }

    private fun modelKey(file: File) = "model_${file.name}"
    private fun nameKey(file: File) = "name_${file.name}"
    private fun displayName(file: File) = prefs.getString(nameKey(file), file.nameWithoutExtension) ?: file.nameWithoutExtension
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun slideIn(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.BounceInRight)) }
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.FadeOutLeft)) }
    }
}
