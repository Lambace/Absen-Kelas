package com.smk.absensikelas

import android.database.Cursor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import com.smk.absensikelas.R
import java.util.*

data class SiswaAbsen(val id: Int, val nama: String, var status: String = "Hadir")

class AbsensiKelasActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idKelas = 0
    private var namaKelas = "-"
    private lateinit var tanggal: String // yyyy-MM-dd
    private val listSiswa = mutableListOf<SiswaAbsen>()

    private val pdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { buatPdf(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_absensi_kelas)

        db = DatabaseHelper(this)
        idKelas = intent.getIntExtra("id_kelas", 0)
        namaKelas = intent.getStringExtra("nama_kelas")?: "-"
        // TERIMA TANGGAL DARI HISTORY, JIKA TIDAK ADA PAKAI HARI INI
        tanggal = intent.getStringExtra("tanggal")
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (idKelas == 0) {
            Toast.makeText(this, "ID Kelas tidak valid", Toast.LENGTH_LONG).show()
            finish(); return
        }

        findViewById<TextView>(R.id.tvJudulKelas).text = "Absensi - $namaKelas"
        val tglView = try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            outFmt.format(inFmt.parse(tanggal)!!)
        } catch (_: Exception) { tanggal }
        findViewById<TextView>(R.id.tvTanggalAbsen).text = "Tanggal: $tglView"

        val rv = findViewById<RecyclerView>(R.id.rvAbsensiKelas)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AdapterAbsen(listSiswa)

        loadSiswa()

        findViewById<View>(R.id.btnDownloadKelas).setOnClickListener {
            if (listSiswa.isEmpty()) {
                Toast.makeText(this, "Tidak ada siswa", Toast.LENGTH_SHORT).show()
            } else {
                pdfLauncher.launch("Absensi_${namaKelas}_${tanggal}.pdf")
            }
        }
    }

    private fun loadSiswa() {
        try {
            listSiswa.clear()
            val c: Cursor = db.readableDatabase.rawQuery(
                "SELECT id, nama_siswa FROM siswa WHERE id_kelas=? ORDER BY nama_siswa ASC",
                arrayOf(idKelas.toString())
            )
            while (c.moveToNext()) {
                val id = c.getInt(0)
                val nama = c.getString(1)
                // CEK STATUS YANG SUDAH ADA DI TANGGAL INI
                val c2 = db.readableDatabase.rawQuery(
                    "SELECT status FROM absensi WHERE id_siswa=? AND tanggal=?",
                    arrayOf(id.toString(), tanggal)
                )
                val status = if (c2.moveToFirst()) c2.getString(0) else "Hadir"
                c2.close()
                listSiswa.add(SiswaAbsen(id, nama, status))
            }
            c.close()
            findViewById<RecyclerView>(R.id.rvAbsensiKelas).adapter?.notifyDataSetChanged()
        } catch (e: Exception) {
            Toast.makeText(this, "Error load: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun simpanAbsen(siswa: SiswaAbsen) {
        try {
            // Hapus dulu lalu insert (aman untuk semua versi SQLite)
            db.writableDatabase.execSQL(
                "DELETE FROM absensi WHERE id_siswa=? AND tanggal=?",
                arrayOf(siswa.id, tanggal)
            )
            db.writableDatabase.execSQL(
                "INSERT INTO absensi (id_siswa, tanggal, status, keterangan) VALUES (?,?,?,?)",
                arrayOf(siswa.id, tanggal, siswa.status, "-")
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal simpan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buatPdf(uri: Uri) {
        try {
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas
            val p = Paint()
            var y = 60
            p.textSize = 18f; p.isFakeBoldText = true
            c.drawText("ABSENSI KELAS $namaKelas", 40f, y.toFloat(), p)
            y += 30; p.textSize = 14f; p.isFakeBoldText = false
            c.drawText("Tanggal: $tanggal", 40f, y.toFloat(), p)
            y += 40
            listSiswa.forEachIndexed { i, s ->
                c.drawText("${i+1}. ${s.nama} - ${s.status}", 40f, y.toFloat(), p)
                y += 22
            }
            pdf.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            pdf.close()
            Toast.makeText(this, "PDF tersimpan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    inner class AdapterAbsen(private val data: List<SiswaAbsen>) :
        RecyclerView.Adapter<AdapterAbsen.VH>() {
        val opsi = arrayOf("Hadir", "Izin", "Sakit", "Alpha", "Bolos")
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val nama: TextView = v.findViewById(R.id.tvNamaSiswaAbsen)
            val sp: Spinner = v.findViewById(R.id.spStatus)
        }
        override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_absensi_siswa, p, false))
        override fun getItemCount() = data.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = data[pos]
            h.nama.text = s.nama
            val adapter = ArrayAdapter(this@AbsensiKelasActivity, android.R.layout.simple_spinner_item, opsi)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            h.sp.adapter = adapter
            h.sp.setSelection(opsi.indexOf(s.status).coerceAtLeast(0), false)
            h.sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, i: Int, l: Long) {
                    if (s.status!= opsi[i]) {
                        s.status = opsi[i]
                        simpanAbsen(s)
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        }
    }
}