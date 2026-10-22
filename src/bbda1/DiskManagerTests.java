package bbda1;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Tests du DiskManager (sans JUnit) : lancer la méthode main. */
public class DiskManagerTests {

    private static int ok = 0, ko = 0;

    private static void check(boolean cond, String nom) {
        if (cond) { ok++; System.out.println("[OK] " + nom); }
        else      { ko++; System.out.println("[ECHEC] " + nom); }
    }

    private static String newDir() throws Exception {
        return Files.createTempDirectory("dmtest").toString();
    }

    private static ByteBuffer buf(int... vals) {
        ByteBuffer b = ByteBuffer.allocate(vals.length);
        for (int v : vals) b.put((byte) v);
        b.flip();
        return b;
    }

    static void testEcritureLecture() throws Exception {
        DiskManager dm = new DiskManager();
        dm.Init(newDir(), 4);
        IPageId p = dm.AllocPage();
        dm.WritePage(p, buf(1, 2, 3, 4));
        ByteBuffer lu = ByteBuffer.allocate(4);
        dm.ReadPage(p, lu);
        lu.flip();
        check(lu.get(0) == 1 && lu.get(3) == 4, "écriture puis lecture");
    }

    static void testPagesIndependantes() throws Exception {
        DiskManager dm = new DiskManager();
        dm.Init(newDir(), 1);
        IPageId a = dm.AllocPage(), b = dm.AllocPage();
        dm.WritePage(a, buf(10));
        dm.WritePage(b, buf(20));
        ByteBuffer x = ByteBuffer.allocate(1), y = ByteBuffer.allocate(1);
        dm.ReadPage(a, x); dm.ReadPage(b, y);
        check(x.get(0) == 10 && y.get(0) == 20 && !a.equals(b), "pages indépendantes");
    }

    static void testReutilisationPageLiberee() throws Exception {
        DiskManager dm = new DiskManager();
        dm.Init(newDir(), 4);
        IPageId a = dm.AllocPage();
        dm.AllocPage();
        dm.DeallocPage(a);
        check(dm.GetActivePageCount() == 1, "compteur après désallocation");
        IPageId c = dm.AllocPage();
        check(c.equals(a), "réutilisation de la page désallouée");
        check(dm.GetActivePageCount() == 2, "compteur après réallocation");
    }

    static void testPersistance() throws Exception {
        String dir = newDir();
        DiskManager dm = new DiskManager();
        dm.Init(dir, 4);
        IPageId a = dm.AllocPage();
        IPageId b = dm.AllocPage();
        dm.WritePage(b, buf(9, 8, 7, 6));
        dm.DeallocPage(a);
        dm.Save();

        DiskManager dm2 = new DiskManager();
        dm2.Init(dir, 4);
        ByteBuffer lu = ByteBuffer.allocate(4);
        dm2.ReadPage(b, lu);
        check(lu.get(0) == 9 && lu.get(3) == 6, "données retrouvées après redémarrage");
        check(dm2.GetActivePageCount() == 1, "compteur retrouvé après redémarrage");
        check(dm2.AllocPage().equals(a), "liste des pages libres retrouvée");
    }

    static void testErreurs() throws Exception {
        DiskManager dm = new DiskManager();
        try { dm.AllocPage(); check(false, "AllocPage avant Init"); }
        catch (DiskManagerException e) { check(true, "AllocPage avant Init -> exception"); }

        String dir = newDir();
        dm.Init(dir, 4);
        try { dm.Init(dir, 4); check(false, "double Init"); }
        catch (DiskManagerException e) { check(true, "double Init -> exception"); }

        IPageId p = dm.AllocPage();
        dm.DeallocPage(p);
        try { dm.DeallocPage(p); check(false, "double désallocation"); }
        catch (DiskManagerException e) { check(true, "double désallocation -> exception"); }
        try { dm.ReadPage(p, ByteBuffer.allocate(4)); check(false, "lecture page libérée"); }
        catch (DiskManagerException e) { check(true, "lecture page libérée -> exception"); }
        try { dm.ReadPage(new PageId(99), ByteBuffer.allocate(4)); check(false, "page inexistante"); }
        catch (DiskManagerException e) { check(true, "page inexistante -> exception"); }
        IPageId q = dm.AllocPage();
        try { dm.ReadPage(q, ByteBuffer.allocate(2)); check(false, "buffer trop petit"); }
        catch (DiskManagerException e) { check(true, "buffer trop petit -> exception"); }

        dm.Save();
        DiskManager autre = new DiskManager();
        try { autre.Init(dir, 8); check(false, "taille de page incompatible"); }
        catch (DiskManagerException e) { check(true, "taille de page incompatible -> exception"); }
    }

    static void testDossierInexistant() throws Exception {
        File d = new File(newDir(), "sous/dossier");
        DiskManager dm = new DiskManager();
        dm.Init(d.getAbsolutePath(), 4);
        check(d.isDirectory(), "dossier inexistant créé par Init");
    }

    public static void main(String[] args) throws Exception {
        testEcritureLecture();
        testPagesIndependantes();
        testReutilisationPageLiberee();
        testPersistance();
        testErreurs();
        testDossierInexistant();
        System.out.println("\nRésultat : " + ok + " OK, " + ko + " échec(s)");
    }
}