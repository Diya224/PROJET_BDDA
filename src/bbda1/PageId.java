package bbda1;
/**
 * Identifiant d'une page sur disque.
 * <p>
 * Une page est identifiée par son numéro (0, 1, 2, ...) dans le fichier de données
 * du DiskManager. La classe est immuable : une fois créé, un PageId ne change plus.
 */
public class PageId implements IPageId {

    private final int pageIdx;

    /**
     * @param pageIdx numéro de la page dans le fichier de données (>= 0)
     */
    public PageId(int pageIdx) {
        this.pageIdx = pageIdx;
    }

    /** @return le numéro de la page dans le fichier de données. */
    public int getPageIdx() {
        return pageIdx;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PageId)) {
            return false;
        }
        return this.pageIdx == ((PageId) obj).pageIdx;
    }

    
   
    @Override
    public int hashCode() {
        return Integer.hashCode(pageIdx);
    }

    @Override
    public String toString() {
        return "PageId[" + pageIdx + "]";
    }
}