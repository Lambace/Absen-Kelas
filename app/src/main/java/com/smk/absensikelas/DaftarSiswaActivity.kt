package com.smk.absensikelas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R
import java.net.URLEncoder

class DaftarSiswaActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: SiswaAdapter
    private var idKelas: Int = 0
    private var namaKelas: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daftar_siswa)

        db = DatabaseHelper(this)
        idKelas = intent.getIntExtra("id_kelas", 0)
        namaKelas = intent.getStringExtra("nama_kelas") ?: ""

        val tvJudulSiswa = findViewById<TextView>(R.id.tvJudulSiswa)
        val rvSiswa = findViewById<RecyclerView>(R.id.rvSiswa)

        // Inisialisasi tombol back yang baru kita tambahkan ID-nya di XML
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        tvJudulSiswa.text = "$namaKelas : Siswa"
        rvSiswa.layoutManager = LinearLayoutManager(this)

        // Aksi klik untuk tombol kembali secara aman
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = SiswaAdapter(
            onEditClick = { s ->
                // === POPUP EDIT LENGKAP ===
                val context = this
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(50, 30, 50, 10)
                }

                val etNama = EditText(context).apply {
                    hint = "Nama Siswa"
                    setText(s.nama)
                }
                val etNis = EditText(context).apply {
                    hint = "NISN"
                    setText(s.nis ?: "")
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
                val spJk = Spinner(context).apply {
                    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                        listOf("Laki-laki", "Perempuan")).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    setSelection(if (s.jenisKelamin == "Perempuan") 1 else 0)
                }

                layout.addView(etNama)
                layout.addView(etNis)
                layout.addView(spJk)

                AlertDialog.Builder(this)
                    .setTitle("Edit Siswa")
                    .setView(layout)
                    .setPositiveButton("Simpan") { _, _ ->
                        val namaBaru = etNama.text.toString().trim()
                        val nisBaru = etNis.text.toString().trim()
                        val jkBaru = spJk.selectedItem.toString()

                        if (namaBaru.isNotEmpty()) {
                            db.writableDatabase.execSQL(
                                "UPDATE siswa SET nama_siswa=?, nis=?, jenis_kelamin=? WHERE id=?",
                                arrayOf(namaBaru, nisBaru, jkBaru, s.id)
                            )
                            reloadData()
                            Toast.makeText(this, "Data diperbarui", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            },
            onDeleteClick = { s ->
                AlertDialog.Builder(this)
                    .setTitle("Hapus Siswa")
                    .setMessage("Hapus ${s.nama}? Data absensi & nilai akan ikut terhapus.")
                    .setPositiveButton("Hapus") { _, _ ->
                        db.writableDatabase.beginTransaction()
                        try {
                            db.writableDatabase.execSQL("DELETE FROM absensi WHERE id_siswa=?", arrayOf(s.id.toString()))
                            db.writableDatabase.execSQL("DELETE FROM nilai WHERE id_siswa=?", arrayOf(s.id.toString()))
                            db.writableDatabase.execSQL("DELETE FROM siswa WHERE id=?", arrayOf(s.id.toString()))
                            db.writableDatabase.setTransactionSuccessful()
                        } finally {
                            db.writableDatabase.endTransaction()
                        }
                        reloadData()
                        Toast.makeText(this, "Terhapus", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            },
            onWaClick = { s ->
                val telp = db.getTelpWaliByKelas(idKelas)
                if (telp.isNotEmpty()) {
                    val namaGuru = db.getGuru()?.get("nama") ?: "Agussalim Tajuddin"
                    val mapel = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("mapel_guru", "Matematika") ?: "Matematika"

                    val (alpha, bolos) = db.getRekapAlphaBolos(s.id)
                    val nisTampil = s.nis?.ifEmpty { "-" } ?: "-"

                    val pesan = """
            Semangat Pagi Bapak/Ibu Wali Kelas hebat. Saya $namaGuru Guru $mapel, Menginformasikan Bahwa Ananda :

            Nama        : ${s.nama}
            Nis         : $nisTampil
            Kelas       : $namaKelas

            Telah tercatat Alpha ${alpha}X dan Bolos ${bolos}X. Mohon agar Siswa tersebut diberi perhatian. Terima Kasih, Dikirim Otomatis Oleh Sistem ABSESNSI SISWA !!.
        """.trimIndent()

                    val nomor = telp.replace(Regex("[^0-9]"), "").let {
                        if (it.startsWith("0")) "62${it.substring(1)}" else it
                    }
                    val url = "https://wa.me/$nomor?text=${URLEncoder.encode(pesan, "UTF-8")}"
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(waIntent)
                } else {
                    Toast.makeText(this, "Nomor WA wali kelas belum diisi", Toast.LENGTH_SHORT).show()
                }
            },
            enableAlert = true,
            showManageButtons = true
        ) { s ->
            startActivity(Intent(this, ProfilSiswaActivity::class.java).apply {
                putExtra("id_siswa", s.id)
                putExtra("nama_siswa", s.nama)
                putExtra("nis_siswa", s.nis ?: "-")
                putExtra("jk_siswa", s.jenisKelamin ?: "-")
                putExtra("nama_kelas", namaKelas)
            })
        }

        rvSiswa.adapter = adapter
        reloadData()
    }

    private fun reloadData() {
        val tvJumlahSiswa = findViewById<TextView>(R.id.tvJumlahSiswa)
        val data = db.getSiswaByKelas(idKelas).map { s ->
            val (alpha, bolos) = db.getRekapAlphaBolos(s.id)
            s.copy(alpha = alpha, bolos = bolos)
        }
        adapter.submitList(data)
        tvJumlahSiswa.text = data.size.toString()
    }
}