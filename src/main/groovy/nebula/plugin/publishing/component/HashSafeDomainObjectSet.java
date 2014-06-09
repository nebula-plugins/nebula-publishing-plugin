package nebula.plugin.publishing.component;


import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Resolves a hashing defect in the DefaultDomainObjectSet that allows for objects to properly removed from the
 * collection.  By added the object to the collection, only after the eventRegister has acted on the object, we are
 * reducing the chances that the hashcode will change again for the object.
 *
 * The defect stems from the eventRegister actions that can mutate the object (and change it's hashcode),
 * after it has been added to the hash store.  This means that this specific object cannot be removed based on it's has
 *
 * @param <T>
 * @author J. Michael McGarr
 */
public class HashSafeDomainObjectSet<T> extends DefaultDomainObjectSet<T> implements DomainObjectSet<T> {

    public HashSafeDomainObjectSet( Class<? extends T> type ) {
        this( type, new ArrayList<T>() );
    }

    public HashSafeDomainObjectSet( Class<? extends T> type, Collection<T> store ) {
        super( type, store );
    }

    protected HashSafeDomainObjectSet( DefaultDomainObjectSet<? super T> store, CollectionFilter<T> filter ) {
        super( store, filter );
    }

    protected HashSafeDomainObjectSet( Class<? extends T> type, Collection<T> store, CollectionEventRegister<T> eventRegister ) {
        super( type, store, eventRegister );
    }

    @Override
    public boolean add(T toAdd) {
        assertMutable();
        if (!getStore().contains( toAdd )) {
            getEventRegister().getAddAction().execute(toAdd);
            return getStore().add(toAdd);
        } else {
            return false;
        }
    }
}
