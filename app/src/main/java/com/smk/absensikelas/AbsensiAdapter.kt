package com.smk.absensikelas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R


class AbsensiAdapter(
    private val listSiswa: List<SiswaModel>,
    private val db: DatabaseHelper,
    private val tanggal: String
) : RecyclerView.Adapter<AbsensiAdapter.ViewHolder>() {

    val statusMap = mutableMapOf<Int, String>()
    private val rekapMap = mutableMapOf<Int, Pair<Int, Int>>()

    init {
        // Preload sekali saja - default Hadir
        for (siswa in listSiswa) {
            val statusLama = db.getStatusAbsen(siswa.id, tanggal)?: "Hadir"
            statusMap[siswa.id] = statusLama
            rekapMap[siswa.id] = db.getRekapAlphaBolos(siswa.id)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tvInitial)
        val tvNama: TextView = view.findViewById(R.id.tvNamaSiswa)
        val tvNisn: TextView = view.findViewById(R.id.tvNisn)
        val tvGender: TextView = view.findViewById(R.id.tvGender)
        val tvRekap: TextView = view.findViewById(R.id.tvRekap)
        val btnStatus: View = view.findViewById(R.id.btnStatus)
        val tvStatusHuruf: TextView = view.findViewById(R.id.tvStatusHuruf)
        val tvStatusLabel: TextView = view.findViewById(R.id.tvStatusLabel)
        val viewStrip: View = view.findViewById(R.id.viewStrip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_absensi, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val siswa = listSiswa[position]

        holder.tvInitial.text = siswa.nama.firstOrNull()?.uppercase()?: "?"
        holder.tvNama.text = siswa.nama
        holder.tvNisn.text = "NISN: ${siswa.nis?: "-"}"
        holder.tvGender.text = siswa.jenisKelamin?: "L"

        val rekap = rekapMap[siswa.id]?: Pair(0, 0)
        holder.tvRekap.text = "Alpha: ${rekap.first} | Bolos: ${rekap.second}"

        val statusSekarang = statusMap[siswa.id]?: "Hadir"
        val (huruf, label, warna) = when (statusSekarang) {
            "Hadir" -> Triple("H", "HADIR", 0xFF00A896.toInt())
            "Izin" -> Triple("I", "IZIN", 0xFF2196F3.toInt())
            "Sakit" -> Triple("S", "SAKIT", 0xFFFF9800.toInt())
            "Alpha" -> Triple("A", "ALPHA", 0xFFF44336.toInt())
            "Bolos" -> Triple("B", "BOLOS", 0xFF9C27B0.toInt())
            else -> Triple("H", "HADIR", 0xFF00A896.toInt())
        }

        holder.tvStatusHuruf.text = huruf
        holder.tvStatusLabel.text = label
        holder.btnStatus.setBackgroundColor(warna)
        holder.viewStrip.setBackgroundColor(warna)

        holder.btnStatus.setOnClickListener {
            val next = when (statusMap[siswa.id]) {
                "Hadir" -> "Izin"
                "Izin" -> "Sakit"
                "Sakit" -> "Alpha"
                "Alpha" -> "Bolos"
                else -> "Hadir"
            }
            statusMap[siswa.id] = next
            notifyItemChanged(position)
        }
    }
    override fun getItemCount(): Int = listSiswa.size
}