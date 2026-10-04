package com.smk.absensikelas

import android.database.Cursor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R

data class AbsenItem(val tanggal: String, val status: String, val keterangan: String)

class RiwayatAbsensiActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idSiswa = 0
    private var namaSiswa = "-"
    private val listAbsen = mutableListOf<AbsenItem>()
    private lateinit var adapter: AbsenAdapter

    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { savePdfToUri(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riwayat_absensi)

        db = DatabaseHelper(this)
        idSiswa = intent.getIntExtra("id_siswa", 0)
        namaSiswa = intent.getStringExtra("nama_siswa")?: "-"

        findViewById<TextView>(R.id.tvJudul).text = "Riwayat Absensi - $namaSiswa"

        val rv = findViewById<RecyclerView>(R.id.rvTanggal)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = AbsenAdapter(listAbsen)
        rv.adapter = adapter

        findViewById<View>(R.id.btnDownloadAbsensi).setOnClickListener {
            if (listAbsen.isEmpty()) {
                Toast.makeText(this, "Belum ada data absensi", Toast.LENGTH_SHORT).show()
            } else {
                val fileName = "Rekap_Absensi_${namaSiswa.replace(" ", "_")}.pdf"
                createPdfLauncher.launch(fileName)
            }
        }
        loadData()
    }

    private fun loadData() {
        listAbsen.clear()
        try {
            // PERBAIKAN: hapus kolom keterangan yang tidak ada
            val c: Cursor = db.readableDatabase.rawQuery(
                "SELECT tanggal, status FROM absensi WHERE id_siswa=? ORDER BY tanggal DESC",
                arrayOf(idSiswa.toString())
            )
            while (c.moveToNext()) {
                listAbsen.add(
                    AbsenItem(
                        c.getString(0)?: "",
                        c.getString(1)?: "",
                        "-" // isi default
                    )
                )
            }
            c.close()
        } catch (e: Exception) {
            Toast.makeText(this, "DB Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        adapter.notifyDataSetChanged()
    }

    private fun savePdfToUri(uri: Uri) {
        try {
            val tanggalHariIni = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdf.startPage(pageInfo)
            val c = page.canvas
            val p = Paint()

            var y = 80f
            // Judul — SAMA dengan rekap nilai
            p.textSize = 14f; p.isFakeBoldText = true
            c.drawText("REKAP ABSENSI SISWA", 40f, y, p); y += 25f

            p.textSize = 11f; p.isFakeBoldText = false
            c.drawText("Nama: $namaSiswa", 40f, y, p); y += 18f
            c.drawText("Tanggal: $tanggalHariIni", 40f, y, p); y += 30f

            // Header tabel
            p.isFakeBoldText = true
            c.drawText("Keterangan", 40f, y, p)
            c.drawText("Status", 300f, y, p)
            c.drawText("Tanggal", 380f, y, p); y += 10f
            c.drawLine(40f, y, 550f, y, p); y += 20f

            // Isi
            p.isFakeBoldText = false
            var totalHadir = 0
            for (item in listAbsen) {
                c.drawText("ABSEN", 40f, y, p) // samakan dengan kolom "NILAI 1"
                c.drawText(item.status.uppercase(), 300f, y, p)
                c.drawText(item.tanggal, 380f, y, p)
                if (item.status.equals("hadir", true)) totalHadir++
                y += 20f
                if (y > 770) break // ganti halaman kalau perlu
            }

            y += 20f
            p.isFakeBoldText = true
            c.drawText("TOTAL HADIR: $totalHadir", 40f, y, p)

            pdf.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            pdf.close()

            Toast.makeText(this, "PDF berhasil disimpan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    class AbsenAdapter(private val data: List<AbsenItem>) : RecyclerView.Adapter<AbsenAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tgl: TextView = v.findViewById(R.id.tvTanggalAbsen)
            val ket: TextView = v.findViewById(R.id.tvKeteranganAbsen)
            val status: TextView = v.findViewById(R.id.tvStatus)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_riwayat_absensi, parent, false))
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = data[p]
            h.tgl.text = item.tanggal
            h.ket.text = item.keterangan
            h.status.text = item.status.uppercase()
            val color = when (item.status.lowercase()) {
                "hadir" -> "#E0F2F1"; "izin","sakit" -> "#FFF3E0"; "alpha","alpa" -> "#FFEBEE"; else -> "#F5F5F5"
            }
            h.status.setBackgroundColor(android.graphics.Color.parseColor(color))
        }
    }
}