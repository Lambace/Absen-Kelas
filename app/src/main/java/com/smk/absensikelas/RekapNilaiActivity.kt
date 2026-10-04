package com.smk.absensikelas

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class RekapNilaiActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var tabelRekap: TableLayout
    private var idKelas: Int = 0
    private var namaKelas: String = ""
    private var tahunAjaran: String = ""
    private var kkmNilai: Int? = null

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { savePdfKelas(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rekap_nilai)

        db = DatabaseHelper(this)
        tabelRekap = findViewById(R.id.tabelRekap)
        idKelas = intent.getIntExtra("id_kelas", 0)
        namaKelas = intent.getStringExtra("nama_kelas") ?: ""
        tahunAjaran = intent.getStringExtra("tahun_ajaran")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("tahun_ajaran", "2025/2026")!!

        findViewById<TextView>(R.id.tvJudulRekap).text = "Rekap Nilai - $namaKelas"
        findViewById<MaterialButton>(R.id.btnExportPdf).apply {
            text = "Download"
            setOnClickListener { exportExcel() }
        }

        // === KKM TANPA DEFAULT ===
        val etKKM = findViewById<EditText>(R.id.etKKM)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        kkmNilai = if (prefs.contains("kkm_nilai")) prefs.getInt("kkm_nilai", 0) else null
        etKKM.setText(kkmNilai?.toString() ?: "")

        etKKM.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = s.toString().toIntOrNull()
                kkmNilai = v
                if (v != null) prefs.edit().putInt("kkm_nilai", v).apply()
                else prefs.edit().remove("kkm_nilai").apply()
                loadTabel()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadTabel()
    }

    private fun getMapelList(): List<String> {
        val list = mutableListOf<String>()
        val c = db.readableDatabase.rawQuery("""
            SELECT DISTINCT mapel FROM nilai 
            WHERE id_siswa IN (SELECT id FROM siswa WHERE id_kelas=?)
            ORDER BY mapel
        """, arrayOf(idKelas.toString()))
        while(c.moveToNext()) list.add(c.getString(0))
        c.close()
        if(list.isEmpty()) list.addAll(listOf("UH1","UTS","UAS"))
        return list
    }

    private fun loadTabel() {
        tabelRekap.removeAllViews()
        val siswaList = db.getSiswaByKelas(idKelas)
        val mapels = getMapelList()

        val header = TableRow(this)
        (listOf("No","Nama","NISN") + mapels + listOf("Rata2")).forEach { t ->
            header.addView(createCell(t, true, if(t=="Nama")200 else 90))
        }
        tabelRekap.addView(header)

        siswaList.forEachIndexed { i, s ->
            val row = TableRow(this)
            var total = 0.0; var cnt = 0
            row.addView(createCell("${i+1}"))
            row.addView(createCell(s.nama, false, 200))
            row.addView(createCell(s.nis ?: "-"))

            mapels.forEach { mp ->
                val d = db.getNilaiDetail(s.id, mp)
                val txt = if(d.nilai>=0) d.nilai.toInt().toString() else "-"
                val tv = createCell(txt, false, 90)
                tv.setOnClickListener { showEdit(s.id, s.nama, mp, d) }
                tv.setBackgroundColor(Color.parseColor("#E0F2F1"))

                // VALIDASI KKM
                if (kkmNilai != null && d.nilai >= 0 && d.nilai < kkmNilai!!) {
                    tv.setTextColor(Color.RED)
                    tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    tv.setTextColor(Color.parseColor("#00695C"))
                    tv.typeface = Typeface.DEFAULT
                }

                row.addView(tv)
                if(d.nilai>=0){ total+=d.nilai; cnt++ }
            }
            row.addView(createCell(if(cnt>0) "%.1f".format(total/cnt) else "-"))
            tabelRekap.addView(row)
        }
    }

    private fun createCell(t: String, head: Boolean = false, w: Int = 80) = TextView(this).apply {
        text = t; gravity = Gravity.CENTER; setPadding(22,22,22,22); minWidth = w; textSize = 14f
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if(head) Color.parseColor("#00A896") else Color.WHITE)
            setStroke(1, Color.parseColor("#E0E0E0"))
        }
        setTextColor(if(head) Color.WHITE else Color.BLACK)
        if(head) typeface = Typeface.DEFAULT_BOLD
    }

    private fun showEdit(idS: Int, nmS: String, mapel: String, detail: DatabaseHelper.NilaiDetail) {
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,20,40,0) }
        val etNilai = EditText(this).apply {
            hint = "Nilai 0-100"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if(detail.nilai>=0) detail.nilai.toInt().toString() else ""); gravity = Gravity.CENTER; textSize = 20f
        }
        val etKet = EditText(this).apply { hint = "Keterangan"; setText(detail.keterangan) }
        val tvTgl = TextView(this).apply {
            text = if(detail.tanggal.isNotEmpty()) "Input: ${detail.tanggal}" else "Belum diinput"
            setTextColor(Color.GRAY); textSize = 12f
        }
        lay.addView(etNilai); lay.addView(etKet); lay.addView(tvTgl)

        AlertDialog.Builder(this).setTitle("$nmS - $mapel").setView(lay)
            .setPositiveButton("Simpan") { _, _ ->
                val v = etNilai.text.toString().toDoubleOrNull()
                if(v!=null) {
                    db.insertOrUpdateNilai(idS, mapel, tahunAjaran, v, etKet.text.toString())
                    loadTabel()
                }
            }
            .setNeutralButton("Hapus"){_,_ -> db.deleteNilai(idS, mapel); loadTabel() }
            .setNegativeButton("Batal", null).show()
    }

    private fun exportExcel() {
        try {
            val safeKelas = namaKelas.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeTahun = tahunAjaran.replace("/", "-")
            val fileName = "Rekap_${safeKelas}_${safeTahun}.pdf"
            createPdfLauncher.launch(fileName)
        } catch (e: Exception) {
            Log.e("EXPORT", "Error", e)
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePdfKelas(uri: Uri) {
        try {
            val siswaList = db.getSiswaByKelas(idKelas)
            val mapels = getMapelList()
            val tanggal = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val c = page.canvas
            val p = Paint()
            var y = 80f

            p.textSize = 14f; p.isFakeBoldText = true
            c.drawText("REKAP NILAI KELAS", 40f, y, p); y += 20f
            p.textSize = 11f; p.isFakeBoldText = false
            c.drawText("Kelas: $namaKelas", 40f, y, p); y += 15f
            c.drawText("Tahun: $tahunAjaran", 40f, y, p); y += 15f
            c.drawText("Tanggal: $tanggal", 40f, y, p); y += 30f

            p.isFakeBoldText = true
            var x = 40f
            c.drawText("No", x, y, p); x += 25f
            c.drawText("Nama", x, y, p); x += 130f
            mapels.forEach { mp -> c.drawText(mp.take(5), x, y, p); x += 45f }
            c.drawText("Rata2", x, y, p)
            y += 10f; c.drawLine(40f, y, 550f, y, p); y += 18f

            p.isFakeBoldText = false
            siswaList.forEachIndexed { i, s ->
                if (y > 770) return@forEachIndexed
                x = 40f
                var total = 0.0; var cnt = 0
                c.drawText("${i+1}", x, y, p); x += 25f
                c.drawText(s.nama.take(18), x, y, p); x += 130f
                mapels.forEach { mp ->
                    val d = db.getNilaiDetail(s.id, mp)
                    val txt = if(d.nilai>=0) d.nilai.toInt().toString() else "-"
                    c.drawText(txt, x, y, p)
                    if(d.nilai>=0){ total+=d.nilai; cnt++ }
                    x += 45f
                }
                c.drawText(if(cnt>0) "%.1f".format(total/cnt) else "-", x, y, p)
                y += 20f
            }

            pdf.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            pdf.close()

            Toast.makeText(this, "PDF berhasil disimpan", Toast.LENGTH_LONG).show()
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Buka PDF"))

        } catch (e: Exception) {
            Log.e("EXPORT", "PDF Error", e)
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}