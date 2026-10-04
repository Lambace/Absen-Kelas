package com.smk.absensikelas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R
import java.text.SimpleDateFormat
import java.util.*

data class HistoryItem(val tanggal: String, val hadir: Int, val total: Int)

class HistoryAbsensiActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idKelas = 0
    private var namaKelas = ""
    private val list = mutableListOf<HistoryItem>()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { exportHistory(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_absensi)

        db = DatabaseHelper(this)
        idKelas = intent.getIntExtra("id_kelas", 0)
        namaKelas = intent.getStringExtra("nama_kelas")?: ""

        if (idKelas == 0) { finish(); return }

        findViewById<TextView>(R.id.tvTitleHistory).text = "Absensi - $namaKelas"
        findViewById<View>(R.id.btnDownloadHistory)?.setOnClickListener {
            exportLauncher.launch("History_Absensi_${namaKelas.replace(" ","_")}.csv")
        }

        findViewById<RecyclerView>(R.id.rvHistory).apply {
            layoutManager = LinearLayoutManager(this@HistoryAbsensiActivity)
            adapter = Adapter()
        }
        loadHistory()
    }

    override fun onResume() { super.onResume(); loadHistory() }

    private fun loadHistory() {
        list.clear()
        val totalSiswa = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM siswa WHERE id_kelas=?", arrayOf(idKelas.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        // PAKAI STRUKTUR ASLI: absensi(id_siswa, tanggal, status)
        val c = db.readableDatabase.rawQuery("""
            SELECT a.tanggal,
                   IFNULL(SUM(CASE WHEN a.status='Hadir' OR a.status='H' THEN 1 ELSE 0 END),0) as hadir
            FROM absensi a
            JOIN siswa s ON a.id_siswa = s.id
            WHERE s.id_kelas=?
            GROUP BY a.tanggal
            ORDER BY a.tanggal DESC
        """.trimIndent(), arrayOf(idKelas.toString()))

        while (c.moveToNext()) {
            list.add(HistoryItem(c.getString(0), c.getInt(1), totalSiswa))
        }
        c.close()
        findViewById<RecyclerView>(R.id.rvHistory).adapter?.notifyDataSetChanged()
    }

    private fun exportHistory(uri: Uri) {
        try {
            val siswa = mutableListOf<Pair<Int,String>>()
            db.readableDatabase.rawQuery(
                "SELECT id, nama_siswa FROM siswa WHERE id_kelas=? ORDER BY nama_siswa",
                arrayOf(idKelas.toString())
            ).use { c -> while (c.moveToNext()) siswa.add(c.getInt(0) to c.getString(1)) }

            val tanggal = mutableListOf<String>()
            db.readableDatabase.rawQuery(
                "SELECT DISTINCT tanggal FROM absensi a JOIN siswa s ON a.id_siswa=s.id WHERE s.id_kelas=? ORDER BY tanggal ASC",
                arrayOf(idKelas.toString())
            ).use { c -> while (c.moveToNext()) tanggal.add(c.getString(0)) }

            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { out ->
                out.write("Nama Siswa")
                tanggal.forEach { t ->
                    val ft = try { SimpleDateFormat("dd-MM-yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t)!!) } catch (_:Exception){ t }
                    out.write(";$ft")
                }
                out.write(";Hadir;Persentase\n")

                for ((idS, nama) in siswa) {
                    out.write("\"$nama\"")
                    var hadir = 0
                    for (tgl in tanggal) {
                        val st = db.readableDatabase.rawQuery(
                            "SELECT status FROM absensi WHERE id_siswa=? AND tanggal=?",
                            arrayOf(idS.toString(), tgl)
                        ).use { c -> if (c.moveToFirst()) c.getString(0) else "A" }
                        val singkat = when(st){"Hadir"->"H";"Sakit"->"S";"Izin"->"I";"H"->"H";"S"->"S";"I"->"I";else->"A"}
                        out.write(";$singkat")
                        if (singkat=="H") hadir++
                    }
                    val persen = if (tanggal.isNotEmpty()) hadir*100.0/tanggal.size else 0.0
                    out.write(";$hadir;${String.format(Locale.GERMAN, "%.1f%%", persen)}\n")
                }
            }
            Toast.makeText(this, "Export berhasil", Toast.LENGTH_LONG).show()
        } catch (e:Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTanggal: TextView = v.findViewById(R.id.tvTanggal)
            val tvHari: TextView = v.findViewById(R.id.tvHari) // ganti dari tvLec/tvAtt
            val btnEdit: View = v.findViewById(R.id.btnEdit)
            val btnDelete: View = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_history_absensi, p, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = list[pos]

            // Parse tanggal dari DB (format yyyy-MM-dd)
            val date = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(item.tanggal)
            } catch (_: Exception) { null }

            // Tampilkan tanggal di atas
            val tgl = if (date!= null) {
                SimpleDateFormat("dd-MM-yyyy", Locale.US).format(date)
            } else item.tanggal
            h.tvTanggal.text = tgl

            // Tampilkan nama hari di bawah
            val hari = if (date!= null) {
                SimpleDateFormat("EEEE", Locale("id", "ID")).format(date)
            } else "-"
            h.tvHari.text = hari

            h.btnEdit.setOnClickListener {
                startActivity(Intent(this@HistoryAbsensiActivity, AbsensiActivity::class.java).apply {
                    putExtra("id_kelas", idKelas)
                    putExtra("nama_kelas", namaKelas)
                    putExtra("tanggal", item.tanggal)
                })
            }

            h.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@HistoryAbsensiActivity)
                    .setMessage("Hapus absensi $tgl ($hari)?")
                    .setPositiveButton("Hapus") {_,_ ->
                        db.writableDatabase.execSQL(
                            "DELETE FROM absensi WHERE tanggal=? AND id_siswa IN (SELECT id FROM siswa WHERE id_kelas=?)",
                            arrayOf(item.tanggal, idKelas.toString())
                        )
                        loadHistory()
                    }
                    .setNegativeButton("Batal", null).show()
            }
        }
    }
}