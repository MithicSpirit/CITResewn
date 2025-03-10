package shcm.shsupercm.fabric.citresewn.pack;

import net.fabricmc.fabric.impl.resource.loader.GroupResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import shcm.shsupercm.fabric.citresewn.CITResewn;
import shcm.shsupercm.fabric.citresewn.ex.CITParseException;
import shcm.shsupercm.fabric.citresewn.mixin.core.GroupResourcePackAccessor;
import shcm.shsupercm.fabric.citresewn.pack.cits.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses cits from resourcepacks
 */
public final class CITParser { private CITParser() {}
    /**
     * CIT type registry.
     */
    public static final Map<String, CITConstructor> REGISTRY = new HashMap<>();
    static {
        REGISTRY.put("item", CITItem::new);
        REGISTRY.put("armor", CITArmor::new);
        REGISTRY.put("elytra", CITElytra::new);
        REGISTRY.put("enchantment", CITEnchantment::new);
    }

    /**
     * Parses cit entries from an ordered collection of resourcepacks.
     * @param packs packs to parse
     * @return a collection of parsed CITs
     */
    public static List<CITPack> parseCITs(Collection<ResourcePack> packs) {
        return packs.stream()
                .map(CITParser::parse)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Parses a resourcepack into a possible collection of citpacks that are contained within.
     * @param resourcePack pack to parse
     * @return a collection of CITPacks or an empty collection if resourcepack contains none
     */
    public static Collection<CITPack> parse(ResourcePack resourcePack) {
        if (FabricLoader.getInstance().isModLoaded("fabric-resource-loader-v0")) {
            Collection<CITPack> group = parseFabricGroup(resourcePack);
            if (group != null)
                return group;
        }

        final CITPack citPack = new CITPack(resourcePack);

        Collection<Identifier> packProperties = new ArrayList<>();
        for (String namespace : resourcePack.getNamespaces(ResourceType.CLIENT_RESOURCES)) {
            packProperties.addAll(resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "citresewn/cit", Integer.MAX_VALUE - 53, s -> s.endsWith(".properties")));
            if (resourcePack.contains(ResourceType.CLIENT_RESOURCES, new Identifier(namespace, "citresewn/cit.properties")))
                packProperties.add(new Identifier(namespace, "citresewn/cit.properties"));

            packProperties.addAll(resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "optifine/cit", Integer.MAX_VALUE - 53, s -> s.endsWith(".properties")));
            if (resourcePack.contains(ResourceType.CLIENT_RESOURCES, new Identifier(namespace, "optifine/cit.properties")))
                packProperties.add(new Identifier(namespace, "optifine/cit.properties"));

            packProperties.addAll(resourcePack.findResources(ResourceType.CLIENT_RESOURCES, namespace, "mcpatcher/cit", Integer.MAX_VALUE - 53, s -> s.endsWith(".properties")));
            if (resourcePack.contains(ResourceType.CLIENT_RESOURCES, new Identifier(namespace, "mcpatcher/cit.properties")))
                packProperties.add(new Identifier(namespace, "mcpatcher/cit.properties"));
        }

        boolean readGlobalProperties = false;
        for (Iterator<Identifier> iterator = packProperties.iterator(); iterator.hasNext(); ) {
            Identifier propertiesIdentifier = iterator.next();
            try {
                if (StringUtils.countMatches(propertiesIdentifier.getPath(), '/') <= 2 && propertiesIdentifier.getPath().endsWith("cit.properties")) {
                    iterator.remove();
                    if (!readGlobalProperties)
                        try (InputStream is = resourcePack.open(ResourceType.CLIENT_RESOURCES, propertiesIdentifier)) {
                            Properties citProperties = new Properties();
                            citProperties.load(is);
                            citPack.loadGlobalProperties(citProperties);
                            readGlobalProperties = true;
                        }
                }
            } catch (Exception e) {
                CITResewn.logErrorLoading("Skipped global properties: " + e.getMessage() + " in " + resourcePack.getName() + " -> " + propertiesIdentifier);
            }
        }

        packProperties.stream()
                .flatMap(citIdentifier -> {
                    try (InputStream is = resourcePack.open(ResourceType.CLIENT_RESOURCES, citIdentifier)) {
                        Properties citProperties = new Properties();
                        citProperties.load(is);

                        CITConstructor type = REGISTRY.get(citProperties.getProperty("type", "item"));
                        if (type == null)
                            throw new CITParseException(citPack.resourcePack, citIdentifier, "Unknown cit type \"" + citProperties.getProperty("type") + "\"");

                        return Stream.of(type.cit(citPack, citIdentifier, citProperties));
                    } catch (Exception e) {
                        CITResewn.logErrorLoading(e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toCollection(() -> citPack.cits));

        if (citPack.cits.isEmpty())
            return Collections.emptySet();
        else {
            CITResewn.info("Found " + citPack.cits.size() + " CIT" + (citPack.cits.size() == 1 ? "" : "s") + " in " + resourcePack.getName());
            return Collections.singleton(citPack);
        }
    }

    public static Collection<CITPack> parseFabricGroup(ResourcePack resourcePack) {
        if (!(resourcePack instanceof GroupResourcePack))
            return null;

        return ((GroupResourcePackAccessor) resourcePack).getPacks().stream()
                        .map(CITParser::parse)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
    }

    public interface CITConstructor {
        CIT cit(CITPack pack, Identifier identifier, Properties properties) throws CITParseException;
    }
}
