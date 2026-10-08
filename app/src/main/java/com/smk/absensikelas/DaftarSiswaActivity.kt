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
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        tvJudulSiswa.text = "$namaKelas : Siswa"
        rvSiswa.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = SiswaAdapter(
            onEditClick = { s ->
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
                    .setMessage("Hapus ${s.nama}? Data absensi, nilai, & orang tua akan ikut terhapus.")
                    .setPositiveButton("Hapus") { _, _ ->
                        db.writableDatabase.beginTransaction()
                        try {
                            db.writableDatabase.execSQL("DELETE FROM absensi WHERE id_siswa=?", arrayOf(s.id.toString()))
                            db.writableDatabase.execSQL("DELETE FROM nilai WHERE id_siswa=?", arrayOf(s.id.toString()))
                            // TAMBAHAN: Hapus juga data orang tua terkait siswa ini
                            db.writableDatabase.execSQL("DELETE FROM orang_tua WHERE id_siswa=?", arrayOf(s.id.toString()))
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
                // === LOGIKA BARU: TAMPILKAN DIALOG PILIHAN PENERIMA ===
                tampilkanPilihanPenerimaWa(s)
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

    // =========================================================
    // === FITUR BARU: PILIH PENERIMA LAPORAN (WALI KELAS / ORANG TUA) ===
    // =========================================================

    private fun tampilkanPilihanPenerimaWa(s: SiswaModel) {
        val (alpha, bolos) = db.getRekapAlphaBolos(s.id)

        val penerimaLabels = mutableListOf<String>()
        val penerimaNomor = mutableListOf<String>()
        val penerimaTipe = mutableListOf<String>()

        // 1. Nomor Wali Kelas (perilaku lama)
        val telpWaliKelas = db.getTelpWaliByKelas(idKelas).replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("0")) "62${it.substring(1)}" else it
        }
        if (telpWaliKelas.isNotEmpty()) {
            penerimaLabels.add("Wali Kelas")
            penerimaNomor.add(telpWaliKelas)
            penerimaTipe.add("WALI_KELAS")
        }

        // 2. Nomor Orang Tua / Wali Murid (fitur baru)
        db.getOrangTuaBySiswa(s.id).forEach { ot ->
            val nomor = ot.noHp.replace(Regex("[^0-9]"), "").let {
                if (it.startsWith("0")) "62${it.substring(1)}" else it
            }
            if (nomor.isNotEmpty()) {
                penerimaLabels.add("${ot.status} - ${ot.nama}")
                penerimaNomor.add(nomor)
                penerimaTipe.add(ot.status)
            }
        }

        if (penerimaNomor.isEmpty()) {
            Toast.makeText(this, "Nomor WA wali kelas & orang tua belum diisi", Toast.LENGTH_SHORT).show()
            return
        }

        // Jika hanya 1 penerima, langsung kirim tanpa dialog
        if (penerimaNomor.size == 1) {
            bukaWhatsApp(penerimaNomor[0], buatPesanLaporan(s, penerimaTipe[0], alpha, bolos))
            return
        }

        // Jika lebih dari 1, tampilkan dialog pilihan penerima
        AlertDialog.Builder(this)
            .setTitle("Kirim laporan ${s.nama} ke:")
            .setItems(penerimaLabels.toTypedArray()) { _, which ->
                bukaWhatsApp(penerimaNomor[which], buatPesanLaporan(s, penerimaTipe[which], alpha, bolos))
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun buatPesanLaporan(s: SiswaModel, tipePenerima: String, alpha: Int, bolos: Int): String {
        val nisTampil = s.nis?.ifEmpty { "-" } ?: "-"
        return if (tipePenerima == "WALI_KELAS") {
            // Pesan lama untuk wali kelas (tidak diubah)
            val namaGuru = db.getGuru()?.get("nama") ?: "Agussalim Tajuddin"
            val mapel = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("mapel_guru", "Matematika") ?: "Matematika"
            """
Semangat Pagi Bapak/Ibu Wali Kelas hebat. Saya $namaGuru Guru $mapel, Menginformasikan Bahwa Ananda :

Nama        : ${s.nama}
Nis         : $nisTampil
Kelas       : $namaKelas

Telah tercatat Alpha ${alpha}X dan Bolos ${bolos}X. Mohon agar Siswa tersebut diberi perhatian. Terima Kasih, Dikirim Otomatis Oleh Sistem ABSESNSI SISWA !!.
            """.trimIndent()
        } else {
            // Pesan baru khusus untuk orang tua: rekap kehadiran + rata-rata nilai
            val stats = db.getStatistikSiswa(s.id)
            val semuaNilai = db.getSemuaNilai(s.id)
            val rata = if (semuaNilai.isNotEmpty()) semuaNilai.average().toInt() else 0
            """
Yth. Bapak/Ibu (${tipePenerima}) dari ananda ${s.nama}, Kelas $namaKelas.
Berikut kami sampaikan laporan kehadiran dan nilai ananda:

- Hadir : ${stats["hadir"] ?: 0}x
- Izin : ${stats["izin"] ?: 0}x
- Sakit : ${stats["sakit"] ?: 0}x
- Alpha : ${stats["alpha"] ?: 0}x
- Bolos : ${stats["bolos"] ?: 0}x
- Rata-rata Nilai : $rata

Mohon perhatian dan kerja sama Bapak/Ibu. Terima kasih.
(Pesan dikirim otomatis oleh Sistem Absensi Siswa)
            """.trimIndent()
        }
    }

    private fun bukaWhatsApp(nomor: String, pesan: String) {
        val url = "https://wa.me/$nomor?text=${URLEncoder.encode(pesan, "UTF-8")}"
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(waIntent)
    }
}