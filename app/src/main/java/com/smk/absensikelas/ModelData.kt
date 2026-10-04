package com.smk.absensikelas
// 1. Profil guru, cocok dengan tabel 'guru'
data class GuruModel(
    val id: Int = 1,
    val nama: String,
    val fotoPath: String?
)

// 2. Kelas, cocok dengan tabel 'kelas' (tanpa kolom 'tingkat' karena memang tidak ada di DB)
data class KelasModel(
    val id: Int = 0,
    val namaKelas: String,
    val jurusan: String,
    val namaWali: String,
    val telpWali: String
)

// 3. Siswa inti, HAPUS alpa/bolos/nilai dari sini (itu hasil hitung, bukan disimpan)

data class SiswaModel(
    val id: Int,
    val nama: String, // dari nama_siswa
    val nis: String? = null, // dari nis
    val jenisKelamin: String? = null, // dari jenis_kelamin
    val idKelas: Int = 0,
    val alpha: Int = 0, // dihitung, bukan dari DB
    val bolos: Int = 0 // dihitung, bukan dari DB
)

// 4. Model bantu untuk nanti migrasi ke Room
data class AbsensiModel(
    val id: Int = 0,
    val idSiswa: Int,
    val tanggal: String,
    val status: String // gunakan "Alpha" konsisten, bukan "Alpa"
)

data class NilaiModel(
    val id: Int = 0,
    val idSiswa: Int,
    val mapel: String,
    val nilai: Int,
    val tanggal: String,
    val keterangan: String?
)

// 5. Untuk StatistikKelasActivity, tetap sama
data class SiswaStatistikModel(
    val id: Int,
    val nama: String,
    val hadir: Int,
    val izin: Int,
    val sakit: Int,
    val alpha: Int,
    val bolos: Int,
    val persentaseHadir: Float,
    val rataNilai: Double
)