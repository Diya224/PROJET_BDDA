package bbda1;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gestionnaire de l'espace disque.
 * <p>
 * Organisation dans le dossier de travail :
 * <ul>
 *   <li><b>dm.data</b> : toutes les pages, les unes derrière les autres
 *       (la page n se trouve à l'octet n * pageSize) ;</li>
 *   <li><b>dm.meta</b> : les informations du DiskManager (taille de page,
 *       nombre de pages, liste des pages libres), écrites par Save().</li>
 * </ul>
 */
public class DiskManager implements IDiskManager {

    private static final String DATA_FILE = "dm.data";
    private static final String META_FILE = "dm.meta";

    private boolean initialized = false;   // Init déjà appelé ?
    private File dataFile;
    private File metaFile;
    private int pageSize;
    private int totalPages;                 // pages existantes dans dm.data
    private final Set<Integer> freePages = new LinkedHashSet<>(); // pages désallouées

    /** Constructeur sans argument (exigé par le sujet). */
    public DiskManager() {
    }

    // ------------------------------------------------------------------ Init

    @Override
    public void Init(String dmDir, int pageSize) throws DiskManagerException {
        if (initialized) {
            throw new DiskManagerException("Init a déjà été appelé : le DiskManager est déjà lié à un dossier.");
        }
        if (dmDir == null || dmDir.trim().isEmpty()) {
            throw new DiskManagerException("Le chemin du dossier est null ou vide.");
        }
        if (pageSize <= 0) {
            throw new DiskManagerException("La taille de page doit être strictement positive : " + pageSize);
        }

        File dir = new File(dmDir);
        if (dir.exists() && !dir.isDirectory()) {
            throw new DiskManagerException("Le chemin existe mais n'est pas un dossier : " + dmDir);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DiskManagerException("Impossible de créer le dossier : " + dmDir);
        }

        File data = new File(dir, DATA_FILE);
        File meta = new File(dir, META_FILE);

        // On travaille sur des variables locales : si une erreur survient,
        // l'objet reste dans son état "non initialisé".
        int newTotal = 0;
        Set<Integer> newFree = new LinkedHashSet<>();

        try {
            if (meta.exists()) {
                // Cas 1 : le dossier contient déjà des données -> on les réutilise.
                try (DataInputStream in = new DataInputStream(Files.newInputStream(meta.toPath()))) {
                    int storedPageSize = in.readInt();
                    if (storedPageSize != pageSize) {
                        throw new DiskManagerException("Taille de page incompatible : le dossier a été créé avec "
                                + storedPageSize + " octets, mais Init demande " + pageSize + ".");
                    }
                    newTotal = in.readInt();
                    int nbFree = in.readInt();
                    if (newTotal < 0 || nbFree < 0 || nbFree > newTotal) {
                        throw new DiskManagerException("Fichier de métadonnées corrompu.");
                    }
                    for (int i = 0; i < nbFree; i++) {
                        int p = in.readInt();
                        if (p < 0 || p >= newTotal || !newFree.add(p)) {
                            throw new DiskManagerException("Fichier de métadonnées corrompu (page libre invalide).");
                        }
                    }
                }
                if (!data.exists() || data.length() != (long) newTotal * pageSize) {
                    throw new DiskManagerException("Le fichier de données est absent ou de taille incohérente.");
                }
            } else {
                // Cas 2 : dossier vide -> on crée un fichier de données vide.
                Files.write(data.toPath(), new byte[0]);
            }
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'entrée/sortie pendant Init.", e);
        }

        this.dataFile = data;
        this.metaFile = meta;
        this.pageSize = pageSize;
        this.totalPages = newTotal;
        this.freePages.clear();
        this.freePages.addAll(newFree);
        this.initialized = true;

        Save(); // le dossier est ainsi immédiatement dans un état valide
    }

    // ------------------------------------------------------------------ Save

    @Override
    public void Save() throws DiskManagerException {
        checkInitialized();
        File tmp = new File(metaFile.getParentFile(), META_FILE + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp.toPath()))) {
                out.writeInt(pageSize);
                out.writeInt(totalPages);
                out.writeInt(freePages.size());
                for (int p : freePages) {
                    out.writeInt(p);
                }
            }
            // On écrit d'abord dans un fichier temporaire puis on remplace :
            // si ça plante en route, l'ancien fichier meta reste intact.
            Files.move(tmp.toPath(), metaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'entrée/sortie pendant Save.", e);
        }
    }

    // ------------------------------------------------------------- AllocPage

    @Override
    public IPageId AllocPage() throws DiskManagerException {
        checkInitialized();

        // 1) Une page désallouée est disponible : on la réutilise.
        if (!freePages.isEmpty()) {
            Iterator<Integer> it = freePages.iterator();
            int idx = it.next();
            it.remove();
            return new PageId(idx);
        }

        // 2) Sinon on ajoute une page (remplie de zéros) à la fin du fichier.
        try (RandomAccessFile raf = new RandomAccessFile(dataFile, "rw")) {
            raf.seek((long) totalPages * pageSize);
            raf.write(new byte[pageSize]);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'entrée/sortie pendant AllocPage.", e);
        }
        return new PageId(totalPages++);
    }

    // -------------------------------------------------------------- ReadPage

    @Override
    public void ReadPage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        checkInitialized();
        int idx = checkAllocatedPage(ipid);
        if (buffer == null) {
            throw new DiskManagerException("Le buffer est null.");
        }
        if (buffer.remaining() < pageSize) {
            throw new DiskManagerException("Buffer trop petit : " + buffer.remaining()
                    + " octets disponibles, " + pageSize + " nécessaires.");
        }
        byte[] bytes = new byte[pageSize];
        try (RandomAccessFile raf = new RandomAccessFile(dataFile, "r")) {
            raf.seek((long) idx * pageSize);
            raf.readFully(bytes);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'entrée/sortie pendant ReadPage.", e);
        }
        buffer.put(bytes); // copie à partir de la position courante du buffer
    }

    // ------------------------------------------------------------- WritePage

    @Override
    public void WritePage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        checkInitialized();
        int idx = checkAllocatedPage(ipid);
        if (buffer == null) {
            throw new DiskManagerException("Le buffer est null.");
        }
        if (buffer.remaining() < pageSize) {
            throw new DiskManagerException("Buffer trop petit : " + buffer.remaining()
                    + " octets disponibles, " + pageSize + " nécessaires.");
        }
        byte[] bytes = new byte[pageSize];
        // duplicate() : on lit sans modifier la position du buffer de l'appelant
        buffer.duplicate().get(bytes);
        try (RandomAccessFile raf = new RandomAccessFile(dataFile, "rw")) {
            raf.seek((long) idx * pageSize);
            raf.write(bytes);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'entrée/sortie pendant WritePage.", e);
        }
    }

    // ----------------------------------------------------------- DeallocPage

    @Override
    public void DeallocPage(IPageId ipid) throws DiskManagerException {
        checkInitialized();
        int idx = checkAllocatedPage(ipid);
        freePages.add(idx);
    }

    // ---------------------------------------------------------------- Getters

    @Override
    public int GetActivePageCount() {
        return initialized ? totalPages - freePages.size() : 0;
    }

    @Override
    public int GetPageSize() {
        return initialized ? pageSize : 0;
    }

    // ----------------------------------------------------- Vérifications privées

    private void checkInitialized() throws DiskManagerException {
        if (!initialized) {
            throw new DiskManagerException("Le DiskManager n'est pas initialisé : appelez Init d'abord.");
        }
    }

    /** Vérifie que l'identifiant est valide ET désigne une page actuellement allouée. */
    private int checkAllocatedPage(IPageId ipid) throws DiskManagerException {
        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException("Identifiant de page null ou de type inconnu.");
        }
        int idx = ((PageId) ipid).getPageIdx();
        if (idx < 0 || idx >= totalPages) {
            throw new DiskManagerException("Page inexistante : " + ipid);
        }
        if (freePages.contains(idx)) {
            throw new DiskManagerException("Page désallouée : " + ipid);
        }
        return idx;
    }
}