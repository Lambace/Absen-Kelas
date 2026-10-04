package com.smk.absensikelas

import android.app.AlertDialog
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.content.Intent
import com.google.android.material.textfield.TextInputEditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R
import java.text.SimpleDateFormat
import java.util.*

data class NilaiItem(val id: Int, val keterangan: String, val nilai: Int, val tanggal: String)

class RiwayatNilaiActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idSiswa = 0
    private var namaSiswa = "-"
    private lateinit var adapter: NilaiAdapter
    private val listNilai = mutableListOf<NilaiItem>()
    private val createPdfLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { savePdfToUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riwayat_nilai)

        db = DatabaseHelper(this)
        idSiswa = intent.getIntExtra("id_siswa", 0)
        namaSiswa = intent.getStringExtra("nama_siswa")?: "-"

        findViewById<TextView>(R.id.tvJudulNilai).text = "Daftar Nilai - $namaSiswa"

        val rv = findViewById<RecyclerView>(R.id.rvNilai)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = NilaiAdapter(listNilai) { item -> showEditDialog(item) }
        rv.adapter = adapter

        findViewById<Button>(R.id.btnDownload).setOnClickListener {
            downloadRekapPdf()
        }

        loadData()
    }

    private fun loadData() {
        listNilai.clear()
        val c: Cursor = db.readableDatabase.rawQuery(
            "SELECT id, keterangan, nilai, tanggal FROM nilai WHERE id_siswa=? ORDER BY tanggal DESC",
            arrayOf(idSiswa.toString())
        )
        while (c.moveToNext()) {
            listNilai.add(
                NilaiItem(
                    c.getInt(0),
                    c.getString(1)?: "-",
                    c.getInt(2),
                    c.getString(3)?: ""
                )
            )
        }
        c.close()
        adapter.notifyDataSetChanged()
    }

    private fun showEditDialog(item: NilaiItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_nilai, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvKet = dialogView.findViewById<TextView>(R.id.tvDialogKeterangan)
        val tvTgl = dialogView.findViewById<TextView>(R.id.tvDialogTanggal)
        val etNilai = dialogView.findViewById<TextInputEditText>(R.id.etNilaiEdit)
        val btnPlus = dialogView.findViewById<ImageButton>(R.id.btnPlus)
        val btnMinus = dialogView.findViewById<ImageButton>(R.id.btnMinus)
        val btnSimpan = dialogView.findViewById<Button>(R.id.btnSimpan)
        val btnBatal = dialogView.findViewById<Button>(R.id.btnBatal)

        tvKet.text = item.keterangan.uppercase()
        tvTgl.text = "Diinput: ${item.tanggal}"
        etNilai.setText(item.nilai.toString())

        btnPlus.setOnClickListener {
            val v = (etNilai.text.toString().toIntOrNull() ?: 0) + 1
            if (v <= 100) etNilai.setText(v.toString())
        }
        btnMinus.setOnClickListener {
            val v = (etNilai.text.toString().toIntOrNull() ?: 0) - 1
            if (v >= 0) etNilai.setText(v.toString())
        }

        btnBatal.setOnClickListener { dialog.dismiss() }

        btnSimpan.setOnClickListener {
            val nilaiBaru = etNilai.text.toString().toIntOrNull() ?: item.nilai
            db.writableDatabase.execSQL(
                "UPDATE nilai SET nilai=? WHERE id=?",
                arrayOf(nilaiBaru, item.id)
            )
            Toast.makeText(this, "${item.keterangan} diubah ke $nilaiBaru", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadData()
        }

        dialog.show()
    }

    private fun downloadRekapPdf() {
        if (listNilai.isEmpty()) {
            Toast.makeText(this, "Belum ada nilai", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "Rekap_Nilai_${namaSiswa.replace(" ", "_")}.pdf"
        // Langsung buka picker - user pilih lokasi
        createPdfLauncher.launch(fileName)
    }

    private fun savePdfToUri(uri: android.net.Uri) {
        try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            var y = 60
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("REKAP NILAI SISWA", 40f, y.toFloat(), paint)
            y += 30
            paint.textSize = 14f
            paint.isFakeBoldText = false
            canvas.drawText("Nama: $namaSiswa", 40f, y.toFloat(), paint)
            y += 20
            canvas.drawText("Tanggal: ${SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())}", 40f, y.toFloat(), paint)
            y += 40
            paint.isFakeBoldText = true
            canvas.drawText("Keterangan", 40f, y.toFloat(), paint)
            canvas.drawText("Nilai", 300f, y.toFloat(), paint)
            canvas.drawText("Tanggal", 400f, y.toFloat(), paint)
            y += 20
            canvas.drawLine(40f, y.toFloat(), 550f, y.toFloat(), paint)
            y += 20
            paint.isFakeBoldText = false
            var total = 0
            for (item in listNilai) {
                canvas.drawText(item.keterangan, 40f, y.toFloat(), paint)
                canvas.drawText(item.nilai.toString(), 300f, y.toFloat(), paint)
                canvas.drawText(item.tanggal, 400f, y.toFloat(), paint)
                y += 25
                total += item.nilai
            }
            y += 20
            paint.isFakeBoldText = true
            val rata = if (listNilai.isNotEmpty()) total / listNilai.size else 0
            canvas.drawText("RATA-RATA: $rata", 40f, y.toFloat(), paint)

            pdf.finishPage(page)

            contentResolver.openOutputStream(uri)?.use { out ->
                pdf.writeTo(out)
            }
            pdf.close()

            Toast.makeText(this, "PDF berhasil disimpan", Toast.LENGTH_LONG).show()

            // Langsung buka preview
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Buka PDF dengan"))

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    // ===== ADAPTER DI DALAM ACTIVITY, KURUNG SUDAH BENAR =====
    class NilaiAdapter(
        private val data: List<NilaiItem>,
        private val onEdit: (NilaiItem) -> Unit
    ) : RecyclerView.Adapter<NilaiAdapter.VH>() {

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNama: TextView = itemView.findViewById(R.id.tvNamaUjian)
            val tvNilai: TextView = itemView.findViewById(R.id.tvNilaiAngka)
            val tvTanggal: TextView = itemView.findViewById(R.id.tvTanggal)
            val btnEdit: Button = itemView.findViewById(R.id.btnEditNilai)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_riwayat_nilai, parent, false)
            return VH(view)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = data[position]
            holder.tvNama.text = item.keterangan
            holder.tvNilai.text = item.nilai.toString()
            holder.tvTanggal.text = "Diinput: ${item.tanggal}"
            holder.btnEdit.setOnClickListener { onEdit(item) }
        }
    }
}