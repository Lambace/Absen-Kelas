package com.smk.absensikelas

import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smk.absensikelas.R
    private lateinit var tvInfoAyah: TextView
    private lateinit var tvInfoIbu: TextView
    private lateinit var btnAturOrangTua: Button

class ProfilSiswaActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idSiswa: Int = 0
    private var namaSiswa: String = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profil_siswa)

        db = DatabaseHelper(this)

        // ambil data dari intent
        idSiswa = intent.getIntExtra("id_siswa", 0)
        namaSiswa = intent.getStringExtra("nama_siswa") ?: "-"
        val nis = intent.getStringExtra("nis_siswa") ?: "-"
        val jk = intent.getStringExtra("jk_siswa") ?: "-"
        val kelas = intent.getStringExtra("nama_kelas") ?: ""

        // HEADER
        findViewById<TextView>(R.id.tvNamaProfil).text = namaSiswa
        findViewById<TextView>(R.id.tvNisnProfil).text = "NISN : $nis"
        findViewById<TextView>(R.id.tvJkProfil).text = "Jenis Kelamin : $jk"
        title = "$kelas : Profil"

        findViewById<Button>(R.id.btnUbahKelas).setOnClickListener {
            Toast.makeText(this, "Profil $namaSiswa", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnLihatNilai).setOnClickListener {
            val i = Intent(this, RiwayatNilaiActivity::class.java)
            i.putExtra("id_siswa", idSiswa)
            i.putExtra("nama_siswa", namaSiswa)
            startActivity(i)
        }

        // === INI YANG DIPERBAIKI ===
        findViewById<Button>(R.id.btnLihatAbsen).setOnClickListener {
            val i = Intent(this, RiwayatAbsensiActivity::class.java)
            i.putExtra("id_siswa", idSiswa)
            i.putExtra("nama_siswa", namaSiswa)
            startActivity(i)
        }

        // === TABEL ABSEN ===
        val tabelAbsen = findViewById<TableLayout>(R.id.tabelAbsen)
        var hadir = 0; var izin = 0; var sakit = 0; var alpha = 0; var bolos = 0

        val cAbs = db.readableDatabase.rawQuery(
            "SELECT status, COUNT(*) FROM absensi WHERE id_siswa=? GROUP BY status",
            arrayOf(idSiswa.toString())
        )
        while (cAbs.moveToNext()) {
            when (cAbs.getString(0)) {
                "Hadir" -> hadir = cAbs.getInt(1)
                "Izin" -> izin = cAbs.getInt(1)
                "Sakit" -> sakit = cAbs.getInt(1)
                "Alpha", "Alpa" -> alpha = cAbs.getInt(1)
                "Bolos" -> bolos = cAbs.getInt(1)
            }
        }
        cAbs.close()
        val total = hadir + izin + sakit + alpha + bolos
        tambahBarisAbsen(tabelAbsen, "HADIR", hadir, total)
        tambahBarisAbsen(tabelAbsen, "IZIN", izin, total)
        tambahBarisAbsen(tabelAbsen, "SAKIT", sakit, total)
        tambahBarisAbsen(tabelAbsen, "ALPA", alpha, total)
        tambahBarisAbsen(tabelAbsen, "BOLOS", bolos, total)
        tambahBarisAbsen(tabelAbsen, "TOTAL", total, total, true)

        // === TABEL NILAI ===
        val tabelNilai = findViewById<TableLayout>(R.id.tabelNilai)
        var totalNilai = 0
        var jumlahNilai = 0

        try {
            val c: Cursor = db.readableDatabase.rawQuery(
                "SELECT IFNULL(keterangan, mapel) as nama, nilai FROM nilai WHERE id_siswa=? ORDER BY tanggal ASC",
                arrayOf(idSiswa.toString())
            )
            while (c.moveToNext()) {
                val namaUjian = c.getString(0) ?: "-"
                val nilai = c.getInt(1)
                tambahBarisNilai(tabelNilai, namaUjian.uppercase(), nilai)
                totalNilai += nilai
                jumlahNilai++
            }
            c.close()

            val rata = if (jumlahNilai > 0) totalNilai / jumlahNilai else 0
            tambahBarisNilai(tabelNilai, "RATA-RATA", rata, true)

        } catch (e: Exception) {
            tambahBarisNilai(tabelNilai, "RATA-RATA", 0, true)
        }
    }

    private fun tambahBarisAbsen(tabel: TableLayout, ket: String, jumlah: Int, total: Int, bold: Boolean = false) {
        val row = TableRow(this)
        val persen = if (total > 0) "${(jumlah * 100 / total)}%" else "0%"
        row.addView(buatCell(ket, bold))
        row.addView(buatCell(jumlah.toString(), bold, true))
        row.addView(buatCell(persen, bold, true))
        tabel.addView(row)
    }

    private fun tambahBarisNilai(tabel: TableLayout, ujian: String, nilai: Int, bold: Boolean = false) {
        val row = TableRow(this)
        row.addView(buatCell(ujian, bold))
        row.addView(buatCell(nilai.toString(), bold, true))
        tabel.addView(row)
    }

    private fun buatCell(teks: String, bold: Boolean = false, center: Boolean = false): TextView {
        return TextView(this).apply {
            text = teks
            setPadding(12, 10, 12, 10)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
            if (bold) setTypeface(null, Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
            setBackgroundResource(android.R.drawable.editbox_background_normal)
        }
    }
}
