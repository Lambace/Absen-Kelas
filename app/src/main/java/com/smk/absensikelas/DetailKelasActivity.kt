package com.smk.absensikelas

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R
import java.text.SimpleDateFormat
import java.util.*

class DetailKelasActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idKelas: Int = 0
    private var namaKelas: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_kelas)

        db = DatabaseHelper(this)

        idKelas = intent.getIntExtra("id_kelas", 0)
        namaKelas = intent.getStringExtra("nama_kelas") ?: ""

        // Header
        findViewById<TextView>(R.id.tvNamaDetail).text = namaKelas
        findViewById<TextView>(R.id.tvSiswaDetail).text = getJumlahSiswa().toString()
        findViewById<TextView>(R.id.tvAbsenDetail).text = getJumlahAbsenHariIni().toString()

        // === 1. TOMBOL SISWA -> BUKA DAFTAR SISWA ===
        findViewById<View>(R.id.btnStudentsDetail).setOnClickListener {
            val i = Intent(this, DaftarSiswaActivity::class.java)
            i.putExtra("id_kelas", idKelas)
            i.putExtra("nama_kelas", namaKelas)
            startActivity(i)
        }

        // === 2. TOMBOL ABSENSI -> BUKA HISTORY (ada tombol Edit) ===
        findViewById<View>(R.id.btnAttendanceDetail).setOnClickListener {
            val i = Intent(this, HistoryAbsensiActivity::class.java)
            i.putExtra("id_kelas", idKelas)
            i.putExtra("nama_kelas", namaKelas)
            startActivity(i)
        }


        // === 3. TOMBOL NILAI ===
        findViewById<View>(R.id.btnExamsDetail).setOnClickListener {
            val i = Intent(this, RekapNilaiActivity::class.java)
            i.putExtra("id_kelas", idKelas)
            i.putExtra("nama_kelas", namaKelas)
            startActivity(i)
        }

        //=== 4. TOMBOL BACK ===

        findViewById<View>(R.id.btnBackDetail).setOnClickListener {
            finish() // atau onBackPressedDispatcher.onBackPressed()
        }
        // RecyclerView - hapus dummy Item 0-9 kalau masih ada
        val rv = findViewById<RecyclerView>(R.id.rvDetail)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = null // kosongkan, atau isi dengan adapter ringkasan jika mau
    }

    private fun getJumlahSiswa(): Int {
        val c = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM siswa WHERE id_kelas=?", arrayOf(idKelas.toString())
        )
        val jumlah = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return jumlah
    }

    private fun getJumlahAbsenHariIni(): Int {
        val tanggal = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val c = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM absensi a JOIN siswa s ON a.id_siswa=s.id WHERE s.id_kelas=? AND a.tanggal=?",
            arrayOf(idKelas.toString(), tanggal)
        )
        val jumlah = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return jumlah
    }
}