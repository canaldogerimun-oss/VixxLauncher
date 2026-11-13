package com.movtery.zalithlauncher.ui.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.view.SkinPixelEditorView
import com.movtery.zalithlauncher.utils.ZHTools
import java.io.File
import java.io.FileOutputStream

class VixxSkinEditorFragment : FragmentWithAnim(R.layout.fragment_vixx_skin_editor) {
    companion object { const val TAG = "VixxSkinEditorFragment" }

    private lateinit var editor: SkinPixelEditorView
    private lateinit var file: File
    private lateinit var colorLabel: TextView
    private var currentColor = Color.rgb(40, 40, 40)

    private val palette = intArrayOf(
        Color.BLACK, Color.WHITE, Color.RED, Color.rgb(255, 128, 0),
        Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
        Color.MAGENTA, Color.rgb(115, 65, 35), Color.rgb(255, 190, 150),
        Color.rgb(120, 120, 120), Color.rgb(30, 30, 50), Color.rgb(80, 40, 130),
        Color.rgb(190, 70, 255), Color.TRANSPARENT
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        editor = view.findViewById(R.id.skinPixelEditor)
        colorLabel = view.findViewById(R.id.skinEditorColorLabel)
        val prefs = requireContext().getSharedPreferences("vixx_skin_studio", 0)
        val path = prefs.getString("selected_skin", null)
        file = path?.let(::File)?.takeIf { it.isFile }
            ?: File(requireContext().filesDir, "vixx_skins/Steve_Classic.png")
        if (!file.isFile) {
            Toast.makeText(requireContext(), "Nenhuma skin encontrada. Selecione uma skin primeiro.", Toast.LENGTH_LONG).show()
            ZHTools.swapFragmentWithAnim(this, VixxSkinStudioFragment::class.java, VixxSkinStudioFragment.TAG, null)
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null || bitmap.width != 64 || bitmap.height != 64) {
            bitmap?.recycle()
            Toast.makeText(requireContext(), "A textura precisa estar no formato 64×64.", Toast.LENGTH_LONG).show()
            ZHTools.swapFragmentWithAnim(this, VixxSkinStudioFragment::class.java, VixxSkinStudioFragment.TAG, null)
            return
        }
        editor.setBitmap(bitmap)
        bitmap.recycle()
        editor.setColor(currentColor)
        editor.setOnSkinChanged { }

        view.findViewById<View>(R.id.skinEditorBack).setOnClickListener {
            ZHTools.swapFragmentWithAnim(this, VixxSkinStudioFragment::class.java, VixxSkinStudioFragment.TAG, null)
        }
        view.findViewById<View>(R.id.skinEditorSave).setOnClickListener { saveAndReturn() }
        view.findViewById<View>(R.id.skinEditorUndo).setOnClickListener {
            if (!editor.undo()) toast("Nada para desfazer.")
        }
        view.findViewById<View>(R.id.skinEditorRedo).setOnClickListener {
            if (!editor.redo()) toast("Nada para refazer.")
        }
        view.findViewById<View>(R.id.skinEditorPencil).setOnClickListener {
            editor.setEraser(false); editor.setColor(currentColor)
            toast("Lápis selecionado.")
        }
        view.findViewById<View>(R.id.skinEditorEraser).setOnClickListener {
            editor.setEraser(true); toast("Borracha selecionada.")
        }
        view.findViewById<View>(R.id.skinEditorMoreColors).setOnClickListener { showPaletteDialog() }
        buildPalette(view.findViewById(R.id.skinEditorPalette))
    }

    private fun buildPalette(container: LinearLayout) {
        container.removeAllViews()
        palette.take(12).forEach { color ->
            val swatch = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(7)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(9).toFloat()
                    setColor(if (color == Color.TRANSPARENT) Color.rgb(65, 60, 72) else color)
                    setStroke(dp(1), Color.argb(130, 255, 255, 255))
                }
                text = if (color == Color.TRANSPARENT) "×" else ""
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setOnClickListener {
                    currentColor = color
                    editor.setColor(color)
                    colorLabel.text = if (color == Color.TRANSPARENT) "Cor: transparente" else "Cor selecionada"
                }
            }
            container.addView(swatch)
        }
    }

    private fun showPaletteDialog() {
        val labels = arrayOf(
            "Preto", "Branco", "Vermelho", "Laranja", "Amarelo", "Verde", "Ciano", "Azul",
            "Magenta", "Marrom", "Pele clara", "Cinza", "Azul escuro", "Roxo", "Roxo neon", "Transparente"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Escolher cor")
            .setItems(labels) { _, which ->
                currentColor = palette[which]
                editor.setColor(currentColor)
                colorLabel.text = "Cor: ${labels[which]}"
            }.show()
    }

    private fun saveAndReturn() {
        val edited = editor.getEditedBitmap() ?: run { toast("Não há textura para salvar."); return }
        try {
            FileOutputStream(file).use { out ->
                if (!edited.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Falha ao gravar o PNG.")
                }
            }
            edited.recycle()
            toast("Skin salva. A prévia será atualizada.")
            ZHTools.swapFragmentWithAnim(this, VixxSkinStudioFragment::class.java, VixxSkinStudioFragment.TAG, null)
        } catch (e: Exception) {
            edited.recycle()
            toast("Não foi possível salvar: ${e.localizedMessage}")
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

    override fun slideIn(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.BounceInRight)) }
    }
    override fun slideOut(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.FadeOutLeft)) }
    }
}
