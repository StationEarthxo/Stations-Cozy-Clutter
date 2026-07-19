package com.worldbuilder;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "World Builder",
    description = "Build with client-side game scenery and share it as Tilepacks",
    tags = {"world", "builder", "objects", "housing", "tilepack"},
    enabledByDefault = false
)
public class WorldBuilderPlugin extends Plugin
{
    private static final String STORAGE_KEY = "placements";
    private static final String SAFETY_PROBE_KEY = "safetyProbe";
    private static final String BLOCKED_OBJECTS_KEY = "blockedObjects";
    private static final String TRUSTED_OBJECTS_KEY = "trustedObjects";
    private static final int SAFETY_PROBE_TICKS = 5;
    private static final int MAX_UNDO = 50;
    private static final int CATALOGUE_BATCH_SIZE = 750;
    private static final int PLACEMENT_ROTATION_STEP = 128;
    // These objects were incorrectly persisted as blocked by v0.2.0 when the
    // config cache was unavailable during the first LOGGED_IN callback.
    private static final Set<Integer> FALSE_BLOCKS_V020 = new HashSet<>(Arrays.asList(1, 1187, 15872, 42827));
    private static final TypeToken<List<PropPlacement>> PLACEMENT_LIST_TYPE = new TypeToken<List<PropPlacement>>() { };
    private static final TypeToken<Set<Integer>> INTEGER_SET_TYPE = new TypeToken<Set<Integer>>() { };
    private static final Logger log = LoggerFactory.getLogger(WorldBuilderPlugin.class);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private WorldBuilderConfig config;

    @Inject
    private Gson gson;

    @Inject
    private ObjectModelFactory modelFactory;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private KeyManager keyManager;

    private final List<PropPlacement> placements = new ArrayList<>();
    private final Map<String, List<RuneLiteObject>> rendered = new HashMap<>();
    private final Deque<List<PropPlacement>> undo = new ArrayDeque<>();
    private volatile PropPlacement copied;
    private volatile RuneLiteObject placementPreview;
    private volatile WorldPoint placementPreviewPoint;
    private volatile LocalPoint placementPreviewLocalPoint;
    private boolean placementMousePressConsumed;
    private boolean placementMouseClickConsumed;
    private boolean placementPreviewOwnsSafetyProbe;
    private int retryTick;
    private final List<CatalogEntry> catalogue = new ArrayList<>();
    private int[] catalogueIds;
    private int catalogueCursor;
    private boolean catalogueComplete;
    private int quarantinedRawProps;
    private final Set<Integer> blockedObjectIds = new HashSet<>(Collections.singleton(59170));
    private final Set<Integer> trustedObjectIds = new HashSet<>();
    private PropPlacement activeSafetyProbe;
    private int safetyProbeTicks;
    private String recoveredUnsafeName;
    private WorldBuilderPanel panel;
    private NavigationButton navigationButton;

    private final MouseAdapter placementMouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent event)
        {
            placementMouseClickConsumed = false;
            if (copied == null)
            {
                return event;
            }
            if (SwingUtilities.isRightMouseButton(event))
            {
                event.consume();
                placementMousePressConsumed = true;
                placementMouseClickConsumed = true;
                clientThread.invokeLater(() -> cancelPlacement("Placement cancelled."));
                return event;
            }
            if (SwingUtilities.isLeftMouseButton(event) && placementPreviewPoint != null && placementPreview != null
                && placementPreviewLocalPoint != null
                && Perspective.getCanvasTilePoly(client, placementPreviewLocalPoint) != null
                && Perspective.getCanvasTilePoly(client, placementPreviewLocalPoint).contains(event.getPoint()))
            {
                WorldPoint point = placementPreviewPoint;
                event.consume();
                placementMousePressConsumed = true;
                placementMouseClickConsumed = true;
                clientThread.invokeLater(() -> placeCopied(point));
            }
            return event;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent event)
        {
            if (placementMousePressConsumed)
            {
                event.consume();
                placementMousePressConsumed = false;
            }
            return event;
        }

        @Override
        public MouseEvent mouseClicked(MouseEvent event)
        {
            if (placementMouseClickConsumed)
            {
                event.consume();
                placementMouseClickConsumed = false;
            }
            return event;
        }
    };

    private final MouseWheelListener placementMouseWheelListener = new MouseWheelListener()
    {
        @Override
        public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
        {
            PropPlacement selected = copied;
            int wheelRotation = event.getWheelRotation();
            if (selected == null || wheelRotation == 0)
            {
                return event;
            }

            event.consume();
            clientThread.invokeLater(() -> rotatePlacementPreview(selected, wheelRotation));
            return event;
        }
    };

    private final net.runelite.client.input.KeyListener placementKeyListener = new net.runelite.client.input.KeyListener()
    {
        @Override
        public void keyTyped(KeyEvent event)
        {
        }

        @Override
        public void keyPressed(KeyEvent event)
        {
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE && copied != null)
            {
                event.consume();
                clientThread.invokeLater(() -> cancelPlacement("Placement cancelled."));
            }
        }

        @Override
        public void keyReleased(KeyEvent event)
        {
        }
    };

    @Provides
    WorldBuilderConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(WorldBuilderConfig.class);
    }

    @Override
    protected void startUp()
    {
        panel = new WorldBuilderPanel(this);
        navigationButton = NavigationButton.builder()
            .tooltip("World Builder")
            .icon(createNavigationIcon())
            .priority(7)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        mouseManager.registerMouseListener(placementMouseListener);
        mouseManager.registerMouseWheelListener(placementMouseWheelListener);
        keyManager.registerKeyListener(placementKeyListener);
        loadSafetyState();
        loadPlacements();
        clientThread.invokeLater(() ->
        {
            refreshAll();
            beginCatalogue();
        });
    }

    @Override
    protected void shutDown()
    {
        mouseManager.unregisterMouseListener(placementMouseListener);
        mouseManager.unregisterMouseWheelListener(placementMouseWheelListener);
        keyManager.unregisterKeyListener(placementKeyListener);
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
        }
        clearSafetyProbe(false);
        clearPlacementPreview();
        clearRendered();
        modelFactory.clear();
        placements.clear();
        undo.clear();
        copied = null;
        placementPreviewPoint = null;
        placementMousePressConsumed = false;
        catalogue.clear();
        catalogueIds = null;
        catalogueCursor = 0;
        catalogueComplete = false;
        quarantinedRawProps = 0;
        blockedObjectIds.clear();
        trustedObjectIds.clear();
        activeSafetyProbe = null;
        recoveredUnsafeName = null;
        panel = null;
        navigationButton = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            refreshAll();
            beginCatalogue();
            if (quarantinedRawProps > 0)
            {
                message("Quarantined " + quarantinedRawProps + " unsafe or unsupported props. Safe builds are unchanged.");
                quarantinedRawProps = 0;
            }
            if (recoveredUnsafeName != null)
            {
                message("Safety recovery blocked " + recoveredUnsafeName + " after the previous interrupted session.");
                recoveredUnsafeName = null;
            }
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            clearPlacementPreview();
            clearRendered();
            clearSafetyProbe(false);
            modelFactory.clear();
        }
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event)
    {
        clearPlacementPreviewAndProbe();
        refreshAll();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (activeSafetyProbe != null)
        {
            if (++safetyProbeTicks >= SAFETY_PROBE_TICKS)
            {
                completeSafetyProbe();
            }
        }
        // Cache files and models can arrive asynchronously. Retry unresolved props occasionally.
        else if (++retryTick % 2 == 0 && rendered.size() < placements.size())
        {
            spawnUnrendered();
        }
        processCatalogueBatch();
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (copied == null || client.getGameState() != GameState.LOGGED_IN)
        {
            clearPlacementPreviewAndProbe();
            return;
        }

        WorldView worldView = client.getTopLevelWorldView();
        Tile tile = worldView == null ? null : worldView.getSelectedSceneTile();
        if (tile == null)
        {
            clearPlacementPreviewAndProbe();
            return;
        }

        WorldPoint point = WorldPoint.fromLocalInstance(client, tile.getLocalLocation());
        if (point == null)
        {
            clearPlacementPreviewAndProbe();
            return;
        }
        updatePlacementPreview(worldView, tile.getLocalLocation(), point);
    }

    private void updatePlacementPreview(WorldView worldView, LocalPoint localPoint, WorldPoint point)
    {
        if (placementPreview != null && point.equals(placementPreviewPoint))
        {
            return;
        }
        clearPlacementPreview();

        if (blockedObjectIds.contains(copied.objectId))
        {
            cancelPlacement("That object has been blocked by the safety system.");
            return;
        }
        if (!trustedObjectIds.contains(copied.objectId) && activeSafetyProbe != null
            && activeSafetyProbe.objectId != copied.objectId)
        {
            return;
        }

        Model model = modelFactory.create(copied);
        if (model == null)
        {
            return;
        }

        PropPlacement probe = copied.duplicateAt(point.getX(), point.getY(), point.getPlane());
        RuneLiteObject preview = client.createRuneLiteObject();
        preview.setModel(model);
        preview.setLocation(localPoint, worldView.getPlane());
        preview.setOrientation(copied.rotation & 2047);
        preview.setZ(preview.getZ() - copied.height);
        preview.setRadius(Math.max(60, 60 * copied.scale / 128));

        if (!trustedObjectIds.contains(copied.objectId) && activeSafetyProbe == null)
        {
            beginSafetyProbe(probe);
            placementPreviewOwnsSafetyProbe = true;
        }
        try
        {
            preview.setActive(true);
        }
        catch (RuntimeException | AssertionError ex)
        {
            preview.setActive(false);
            blockObject(probe);
            clearSafetyProbe(false);
            copied = null;
            log.warn("Blocked object {} after preview activation failure", probe.objectId, ex);
            return;
        }
        placementPreview = preview;
        placementPreviewPoint = point;
        placementPreviewLocalPoint = localPoint;
    }

    private void rotatePlacementPreview(PropPlacement selected, int wheelRotation)
    {
        if (copied != selected)
        {
            return;
        }

        selected.rotation = (selected.rotation + wheelRotation * PLACEMENT_ROTATION_STEP) & 2047;
        if (placementPreview != null)
        {
            placementPreview.setOrientation(selected.rotation);
        }
    }

    private void clearPlacementPreview()
    {
        if (placementPreview != null)
        {
            placementPreview.setActive(false);
            placementPreview = null;
        }
        placementPreviewPoint = null;
        placementPreviewLocalPoint = null;
    }

    private void clearPlacementPreviewAndProbe()
    {
        clearPlacementPreview();
        if (placementPreviewOwnsSafetyProbe)
        {
            clearSafetyProbe(false);
        }
    }

    private void cancelPlacement(String reason)
    {
        copied = null;
        clearPlacementPreview();
        if (placementPreviewOwnsSafetyProbe)
        {
            clearSafetyProbe(false);
        }
        message(reason);
    }

    private void beginCatalogue()
    {
        if (catalogueIds != null || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        catalogueIds = client.getIndexConfig().getFileIds(6);
        if (catalogueIds == null)
        {
            return;
        }
        Arrays.sort(catalogueIds);
        if (panel != null)
        {
            panel.setCatalogueProgress(0, catalogueIds.length);
        }
    }

    private void processCatalogueBatch()
    {
        if (catalogueComplete || catalogueIds == null)
        {
            beginCatalogue();
            return;
        }
        int end = Math.min(catalogueIds.length, catalogueCursor + CATALOGUE_BATCH_SIZE);
        for (; catalogueCursor < end; catalogueCursor++)
        {
            int objectId = catalogueIds[catalogueCursor];
            try
            {
                byte[] bytes = client.getIndexConfig().loadData(6, objectId);
                if (bytes == null)
                {
                    // Config IDs become available before their archive on a
                    // fresh startup. Pause instead of consuming empty data.
                    if (panel != null)
                    {
                        panel.catalogueWaitingForCache();
                    }
                    return;
                }
                ObjectDefinitionData definition = ObjectDefinitionDecoder.decode(bytes);
                if (!definition.isSafeForCustomRendering() || blockedObjectIds.contains(objectId)
                    || Strings.isNullOrEmpty(definition.name) || "null".equalsIgnoreCase(definition.name))
                {
                    continue;
                }
                int type = definition.modelTypes == null ? 10 : definition.modelTypes[0];
                catalogue.add(new CatalogEntry(objectId, type, definition.name));
            }
            catch (RuntimeException ex)
            {
                log.trace("Skipping catalogue object {}", objectId, ex);
            }
        }
        if (panel != null && catalogueCursor % 3000 < CATALOGUE_BATCH_SIZE)
        {
            panel.setCatalogueProgress(catalogueCursor, catalogueIds.length);
        }
        if (panel != null && catalogueCursor <= CATALOGUE_BATCH_SIZE && !catalogue.isEmpty())
        {
            // Make the browser useful immediately instead of leaving it blank
            // until every object in the game has been inspected.
            panel.showResults(catalogue.stream().limit(24).collect(Collectors.toList()), false);
        }
        if (catalogueCursor >= catalogueIds.length)
        {
            catalogue.sort(Comparator.comparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
            catalogueComplete = true;
            if (panel != null)
            {
                panel.catalogueReady(catalogue.size());
            }
        }
    }

    void searchCatalogue(String query)
    {
        final String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        clientThread.invokeLater(() ->
        {
            List<CatalogEntry> matches = needle.isEmpty()
                ? catalogue.stream().limit(24).collect(Collectors.toList())
                : catalogue.stream()
                    .filter(entry -> entry.name.toLowerCase(Locale.ROOT).contains(needle)
                        || Integer.toString(entry.objectId).equals(needle))
                    .collect(Collectors.toList());
            if (panel != null)
            {
                panel.showResults(matches, catalogueComplete);
            }
        });
    }

    void selectCatalogueEntry(CatalogEntry entry)
    {
        clientThread.invokeLater(() ->
        {
            PropPlacement template = new PropPlacement();
            template.name = entry.name;
            template.objectId = entry.objectId;
            template.objectType = entry.objectType;
            template.objectOrientation = 0;
            selectPlacementTemplate(template,
                "Selected " + entry.name + ". Scroll to rotate, left-click to place; right-click or Escape cancels.");
        });
    }

    void requestPreview(CatalogEntry entry, int generation, Consumer<BufferedImage> callback)
    {
        clientThread.invokeLater(() ->
        {
            if (panel == null || !panel.isPreviewGenerationCurrent(generation))
            {
                return;
            }
            Model model = modelFactory.createPreview(entry.objectId, entry.objectType);
            BufferedImage image = model == null ? null : ModelPreviewRenderer.render(model, 91, 91);
            SwingUtilities.invokeLater(() ->
            {
                if (panel != null && panel.isPreviewGenerationCurrent(generation))
                {
                    callback.accept(image);
                }
            });
        });
    }

    private static BufferedImage createNavigationIcon()
    {
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(95, 205, 228));
        g.fillRoundRect(4, 17, 11, 11, 3, 3);
        g.setColor(new Color(235, 169, 67));
        g.fillRoundRect(17, 17, 11, 11, 3, 3);
        g.setColor(new Color(203, 112, 222));
        g.fillRoundRect(10, 4, 12, 11, 3, 3);
        g.setColor(new Color(255, 255, 255, 150));
        g.drawLine(11, 7, 20, 7);
        g.dispose();
        return icon;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        captureObject(event.getGameObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        captureObject(event.getGroundObject());
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        captureObject(event.getDecorativeObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        captureObject(event.getWallObject());
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (config.requireShift() && !client.isKeyPressed(KeyCode.KC_SHIFT))
        {
            return;
        }

        MenuEntry original = event.getMenuEntry();
        if (event.getType() == MenuAction.EXAMINE_OBJECT.getId())
        {
            addCopyObjectEntry(original, event.getActionParam0(), event.getActionParam1(), event.getIdentifier());
            return;
        }

        MenuAction action = original.getType();
        if (action == MenuAction.WALK || action == MenuAction.SET_HEADING)
        {
            addBuilderMenu(original);
        }
    }

    private void addCopyObjectEntry(MenuEntry original, int sceneX, int sceneY, int objectId)
    {
        WorldView worldView = client.getWorldView(original.getWorldViewId());
        if (worldView == null)
        {
            return;
        }
        TileObject object = findTileObject(worldView, sceneX, sceneY, objectId);
        if (object == null)
        {
            return;
        }

        client.createMenuEntry(-1)
            .setOption("Copy to World Builder")
            .setTarget(original.getTarget())
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> copyObject(object));
    }

    private void addBuilderMenu(MenuEntry original)
    {
        WorldView worldView = client.getWorldView(original.getWorldViewId());
        if (worldView == null || worldView.getSelectedSceneTile() == null)
        {
            return;
        }
        WorldPoint point = WorldPoint.fromLocalInstance(client, worldView.getSelectedSceneTile().getLocalLocation());
        PropPlacement selected = findLastAt(point);

        MenuEntry parent = client.createMenuEntry(-1)
            .setOption("World Builder")
            .setTarget("Tile")
            .setType(MenuAction.RUNELITE);
        Menu menu = parent.createSubMenu();

        if (copied != null)
        {
            menu.createMenuEntry(-1)
                .setOption("Place " + copied.name)
                .setType(MenuAction.RUNELITE)
                .onClick(entry -> placeCopied(point));
        }

        if (selected != null)
        {
            // RuneLite's in-game menu supports one submenu level. Creating an
            // Edit submenu inside the World Builder submenu trips a native
            // client assertion as soon as the player hovers Edit. Keep every
            // edit action at this first, safe submenu level.
            menu.createMenuEntry(-1).setOption("Rotate placed object 45 degrees").setType(MenuAction.RUNELITE)
                .onClick(entry -> mutate(selected, p -> p.rotation = (p.rotation + 256) & 2047));
            menu.createMenuEntry(-1).setOption("Raise placed object 1/4 tile").setType(MenuAction.RUNELITE)
                .onClick(entry -> mutate(selected, p -> p.height = Math.min(2048, p.height + 32)));
            menu.createMenuEntry(-1).setOption("Lower placed object 1/4 tile").setType(MenuAction.RUNELITE)
                .onClick(entry -> mutate(selected, p -> p.height = Math.max(-2048, p.height - 32)));
            menu.createMenuEntry(-1).setOption("Make placed object bigger").setType(MenuAction.RUNELITE)
                .onClick(entry -> mutate(selected, p -> p.scale = Math.min(1024, p.scale + 16)));
            menu.createMenuEntry(-1).setOption("Make placed object smaller").setType(MenuAction.RUNELITE)
                .onClick(entry -> mutate(selected, p -> p.scale = Math.max(16, p.scale - 16)));
            menu.createMenuEntry(-1).setOption("Duplicate placed object").setType(MenuAction.RUNELITE)
                .onClick(entry -> duplicate(selected));
            menu.createMenuEntry(-1).setOption("Delete placed object").setType(MenuAction.RUNELITE)
                .onClick(entry -> delete(selected));
        }

        if (!undo.isEmpty())
        {
            menu.createMenuEntry(-1).setOption("Undo last change").setType(MenuAction.RUNELITE)
                .onClick(entry -> undo());
        }
        menu.createMenuEntry(-1).setOption("Export Tilepack").setType(MenuAction.RUNELITE)
            .onClick(entry -> exportTilepack());
        menu.createMenuEntry(-1).setOption("Import Tilepack").setType(MenuAction.RUNELITE)
            .onClick(entry -> importTilepack());
    }

    private void copyObject(TileObject object)
    {
        Renderable renderable = getRenderable(object);
        Model model = renderable == null ? null : renderable.getModel();
        if (model == null)
        {
            message("That object's model is not currently available.");
            return;
        }

        int objectConfig = getObjectConfig(object);
        int type = objectConfig & 31;
        int orientation = (objectConfig >>> 6) & 3;
        ObjectComposition composition = client.getObjectDefinition(object.getId());
        if (composition.getImpostorIds() != null)
        {
            ObjectComposition impostor = composition.getImpostor();
            if (impostor != null)
            {
                composition = impostor;
            }
        }

        PropPlacement template = new PropPlacement();
        template.name = Strings.isNullOrEmpty(composition.getName()) ? "object " + object.getId() : composition.getName();
        template.objectId = object.getId();
        template.objectType = type;
        template.objectOrientation = orientation;
        modelFactory.capture(template.objectId, type, orientation, model);
        selectPlacementTemplate(template,
            "Copied " + template.name + ". Scroll to rotate, left-click to place; right-click or Escape cancels.");
    }

    private void placeCopied(WorldPoint point)
    {
        if (copied == null || placements.size() >= config.maximumProps())
        {
            message("The prop limit has been reached.");
            return;
        }
        pushUndo();
        PropPlacement placement = copied.duplicateAt(point.getX(), point.getY(), point.getPlane());
        placements.add(placement);
        savePlacements();
        if (placementPreviewOwnsSafetyProbe && activeSafetyProbe != null
            && activeSafetyProbe.objectId == placement.objectId)
        {
            // The probe now protects a saved, placed copy rather than only the
            // cursor ghost. Let it finish in the background even if build mode
            // is cancelled or the mouse leaves the scene.
            placementPreviewOwnsSafetyProbe = false;
        }
        spawnUnrendered();
    }

    private void duplicate(PropPlacement selected)
    {
        PropPlacement template = selected.copy();
        template.id = null;
        selectPlacementTemplate(template,
            "Copied placed " + selected.name + ". Scroll to rotate and left-click to place.");
    }

    private void selectPlacementTemplate(PropPlacement template, String status)
    {
        clearPlacementPreview();
        if (placementPreviewOwnsSafetyProbe)
        {
            clearSafetyProbe(false);
        }
        copied = template;
        message(status);
    }

    private void mutate(PropPlacement selected, PlacementMutation mutation)
    {
        pushUndo();
        mutation.apply(selected);
        savePlacements();
        respawn(selected);
    }

    private void delete(PropPlacement selected)
    {
        pushUndo();
        placements.remove(selected);
        despawn(selected.id);
        savePlacements();
    }

    private void undo()
    {
        if (undo.isEmpty())
        {
            return;
        }
        placements.clear();
        placements.addAll(undo.pop());
        savePlacements();
        refreshAll();
        message("Undid the last World Builder change.");
    }

    private void pushUndo()
    {
        List<PropPlacement> snapshot = placements.stream().map(PropPlacement::copy).collect(Collectors.toList());
        undo.push(snapshot);
        while (undo.size() > MAX_UNDO)
        {
            undo.removeLast();
        }
    }

    private void exportTilepack()
    {
        if (placements.isEmpty())
        {
            message("There are no props to export.");
            return;
        }
        try
        {
            Tilepack pack = new Tilepack();
            pack.props = placements.stream()
                .filter(prop -> !blockedObjectIds.contains(prop.objectId))
                .map(PropPlacement::copy)
                .collect(Collectors.toList());
            if (pack.props.isEmpty())
            {
                message("There are no safe props to export.");
                return;
            }
            String code = TilepackCodec.encode(gson, pack);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            message("Copied a " + pack.props.size() + "-prop Tilepack code to your clipboard.");
        }
        catch (Exception ex)
        {
            log.warn("Unable to export Tilepack", ex);
            message("Could not write the Tilepack to your clipboard.");
        }
    }

    private void importTilepack()
    {
        try
        {
            Object clipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            Tilepack pack = TilepackCodec.decode(gson, String.valueOf(clipboard));
            List<PropPlacement> valid = pack.props.stream()
                .filter(PropPlacement::isValid)
                .filter(prop -> !blockedObjectIds.contains(prop.objectId))
                .collect(Collectors.toList());
            int skipped = pack.props.size() - valid.size();
            if (valid.isEmpty())
            {
                message("That Tilepack contains no safe object-based props.");
                return;
            }
            if (placements.size() + valid.size() > config.maximumProps())
            {
                message("That Tilepack would exceed your prop limit.");
                return;
            }

            pushUndo();
            Set<String> existing = placements.stream().map(p -> p.id).collect(Collectors.toSet());
            int imported = 0;
            for (PropPlacement placement : valid)
            {
                if (existing.add(placement.id))
                {
                    placements.add(placement);
                    imported++;
                }
            }
            savePlacements();
            refreshAll();
            message("Imported " + imported + " props from " + pack.name + "."
                + (skipped > 0 ? " Skipped " + skipped + " unsafe legacy props." : ""));
        }
        catch (Exception ex)
        {
            log.debug("Unable to import Tilepack", ex);
            message("Your clipboard does not contain a valid WB1 Tilepack code.");
        }
    }

    private void loadSafetyState()
    {
        blockedObjectIds.clear();
        blockedObjectIds.add(59170);
        trustedObjectIds.clear();
        readIntegerSet(BLOCKED_OBJECTS_KEY, blockedObjectIds);
        readIntegerSet(TRUSTED_OBJECTS_KEY, trustedObjectIds);
        if (blockedObjectIds.removeAll(FALSE_BLOCKS_V020))
        {
            saveIntegerSet(BLOCKED_OBJECTS_KEY, blockedObjectIds);
        }
        trustedObjectIds.removeAll(blockedObjectIds);

        String probeJson = configManager.getConfiguration(WorldBuilderConfig.GROUP, SAFETY_PROBE_KEY);
        if (!Strings.isNullOrEmpty(probeJson))
        {
            try
            {
                PropPlacement interrupted = gson.fromJson(probeJson, PropPlacement.class);
                if (interrupted != null && interrupted.objectId >= 0)
                {
                    blockedObjectIds.add(interrupted.objectId);
                    trustedObjectIds.remove(interrupted.objectId);
                    recoveredUnsafeName = interrupted.name + " (#" + interrupted.objectId + ")";
                    saveIntegerSet(BLOCKED_OBJECTS_KEY, blockedObjectIds);
                    saveIntegerSet(TRUSTED_OBJECTS_KEY, trustedObjectIds);
                }
            }
            catch (JsonSyntaxException ex)
            {
                log.debug("Discarding invalid safety probe", ex);
            }
            configManager.unsetConfiguration(WorldBuilderConfig.GROUP, SAFETY_PROBE_KEY);
        }
    }

    private void readIntegerSet(String key, Set<Integer> destination)
    {
        String json = configManager.getConfiguration(WorldBuilderConfig.GROUP, key);
        if (Strings.isNullOrEmpty(json))
        {
            return;
        }
        try
        {
            Set<Integer> values = gson.fromJson(json, INTEGER_SET_TYPE.getType());
            if (values != null)
            {
                destination.addAll(values);
            }
        }
        catch (JsonSyntaxException ex)
        {
            log.debug("Ignoring invalid World Builder safety state", ex);
        }
    }

    private void saveIntegerSet(String key, Set<Integer> values)
    {
        configManager.setConfiguration(WorldBuilderConfig.GROUP, key, gson.toJson(values));
    }

    private void loadPlacements()
    {
        placements.clear();
        String json = configManager.getConfiguration(WorldBuilderConfig.GROUP, STORAGE_KEY);
        if (Strings.isNullOrEmpty(json))
        {
            return;
        }
        try
        {
            List<PropPlacement> saved = gson.fromJson(json, PLACEMENT_LIST_TYPE.getType());
            if (saved != null)
            {
                saved.stream()
                    .filter(PropPlacement::isValid)
                    .filter(prop -> !blockedObjectIds.contains(prop.objectId))
                    .limit(config.maximumProps())
                    .forEach(placements::add);
                quarantinedRawProps = saved.size() - placements.size();
                if (quarantinedRawProps > 0)
                {
                    savePlacements();
                }
            }
        }
        catch (JsonSyntaxException ex)
        {
            log.warn("Unable to load World Builder props", ex);
        }
    }

    private void savePlacements()
    {
        if (placements.isEmpty())
        {
            configManager.unsetConfiguration(WorldBuilderConfig.GROUP, STORAGE_KEY);
        }
        else
        {
            configManager.setConfiguration(WorldBuilderConfig.GROUP, STORAGE_KEY, gson.toJson(placements));
        }
    }

    private void refreshAll()
    {
        clearRendered();
        clearSafetyProbe(false);
        spawnUnrendered();
    }

    private void spawnUnrendered()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        for (PropPlacement placement : placements)
        {
            if (rendered.containsKey(placement.id))
            {
                continue;
            }
            if (blockedObjectIds.contains(placement.objectId))
            {
                rendered.put(placement.id, Collections.emptyList());
                continue;
            }

            boolean trusted = trustedObjectIds.contains(placement.objectId);
            if (!trusted && activeSafetyProbe != null && activeSafetyProbe.objectId != placement.objectId)
            {
                // Only one unknown model is tested at a time so an interrupted
                // session can identify the exact offender.
                continue;
            }
            boolean needsProbe = !trusted && activeSafetyProbe == null;
            if (!spawn(placement, needsProbe) && modelFactory.isPermanentlyFailed(placement))
            {
                // Decode/model incompatibility means "not renderable by this
                // version", not "dangerous". Keep the placement intact and
                // avoid retrying it this session; only activation failures or
                // interrupted safety probes earn a persistent block.
                rendered.put(placement.id, Collections.emptyList());
            }
        }
    }

    private boolean spawn(PropPlacement placement, boolean needsProbe)
    {
        Model model = modelFactory.create(placement);
        if (model == null)
        {
            return false;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return false;
        }
        WorldPoint source = new WorldPoint(placement.worldX, placement.worldY, placement.plane);
        Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(worldView, source);
        if (localWorldPoints.isEmpty())
        {
            return false;
        }

        List<RuneLiteObject> objects = new ArrayList<>();
        for (WorldPoint localWorld : localWorldPoints)
        {
            LocalPoint local = LocalPoint.fromWorld(worldView, localWorld);
            if (local == null)
            {
                continue;
            }
            RuneLiteObject object = client.createRuneLiteObject();
            object.setModel(model);
            object.setLocation(local, worldView.getPlane());
            object.setOrientation(placement.rotation & 2047);
            object.setZ(object.getZ() - placement.height);
            object.setRadius(Math.max(60, 60 * placement.scale / 128));
            if (needsProbe && activeSafetyProbe == null)
            {
                beginSafetyProbe(placement);
            }
            try
            {
                object.setActive(true);
            }
            catch (RuntimeException | AssertionError ex)
            {
                object.setActive(false);
                blockObject(placement);
                clearSafetyProbe(false);
                log.warn("Blocked object {} after activation failure", placement.objectId, ex);
                return false;
            }
            objects.add(object);
        }
        if (!objects.isEmpty())
        {
            rendered.put(placement.id, objects);
            return true;
        }
        clearSafetyProbe(false);
        return false;
    }

    private void respawn(PropPlacement placement)
    {
        despawn(placement.id);
        if (activeSafetyProbe == null)
        {
            spawn(placement, !trustedObjectIds.contains(placement.objectId));
        }
    }

    private void despawn(String placementId)
    {
        List<RuneLiteObject> objects = rendered.remove(placementId);
        if (objects != null)
        {
            objects.forEach(object -> object.setActive(false));
        }
    }

    private void clearRendered()
    {
        rendered.values().stream().flatMap(Collection::stream).forEach(object -> object.setActive(false));
        rendered.clear();
    }

    private void beginSafetyProbe(PropPlacement placement)
    {
        activeSafetyProbe = placement.copy();
        safetyProbeTicks = 0;
        configManager.setConfiguration(WorldBuilderConfig.GROUP, SAFETY_PROBE_KEY, gson.toJson(activeSafetyProbe));
    }

    private void completeSafetyProbe()
    {
        if (activeSafetyProbe != null)
        {
            trustedObjectIds.add(activeSafetyProbe.objectId);
            saveIntegerSet(TRUSTED_OBJECTS_KEY, trustedObjectIds);
        }
        clearSafetyProbe(false);
        spawnUnrendered();
    }

    private void clearSafetyProbe(boolean trust)
    {
        if (trust && activeSafetyProbe != null)
        {
            trustedObjectIds.add(activeSafetyProbe.objectId);
            saveIntegerSet(TRUSTED_OBJECTS_KEY, trustedObjectIds);
        }
        activeSafetyProbe = null;
        safetyProbeTicks = 0;
        placementPreviewOwnsSafetyProbe = false;
        configManager.unsetConfiguration(WorldBuilderConfig.GROUP, SAFETY_PROBE_KEY);
    }

    private void blockObject(PropPlacement placement)
    {
        blockedObjectIds.add(placement.objectId);
        trustedObjectIds.remove(placement.objectId);
        if (copied != null && copied.objectId == placement.objectId)
        {
            copied = null;
            clearPlacementPreview();
        }
        saveIntegerSet(BLOCKED_OBJECTS_KEY, blockedObjectIds);
        saveIntegerSet(TRUSTED_OBJECTS_KEY, trustedObjectIds);
        message("Blocked unsupported object " + placement.name + " (#" + placement.objectId + ") for safety.");
    }

    private void captureObject(TileObject object)
    {
        Renderable renderable = getRenderable(object);
        Model model = renderable == null ? null : renderable.getModel();
        if (model == null)
        {
            return;
        }
        int objectConfig = getObjectConfig(object);
        modelFactory.capture(object.getId(), objectConfig & 31, (objectConfig >>> 6) & 3, model);
    }

    private PropPlacement findLastAt(WorldPoint point)
    {
        for (int i = placements.size() - 1; i >= 0; i--)
        {
            PropPlacement p = placements.get(i);
            if (p.worldX == point.getX() && p.worldY == point.getY() && p.plane == point.getPlane())
            {
                return p;
            }
        }
        return null;
    }

    private TileObject findTileObject(WorldView worldView, int sceneX, int sceneY, int id)
    {
        Scene scene = worldView.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = worldView.getPlane();
        if (sceneX < 0 || sceneY < 0 || sceneX >= tiles[plane].length || sceneY >= tiles[plane][sceneX].length)
        {
            return null;
        }
        Tile tile = tiles[plane][sceneX][sceneY];
        if (tile == null)
        {
            return null;
        }
        if (objectIdEquals(tile.getWallObject(), id))
        {
            return tile.getWallObject();
        }
        if (objectIdEquals(tile.getDecorativeObject(), id))
        {
            return tile.getDecorativeObject();
        }
        if (objectIdEquals(tile.getGroundObject(), id))
        {
            return tile.getGroundObject();
        }
        for (GameObject object : tile.getGameObjects())
        {
            if (objectIdEquals(object, id))
            {
                return object;
            }
        }
        return null;
    }

    private boolean objectIdEquals(TileObject object, int id)
    {
        if (object == null)
        {
            return false;
        }
        if (object.getId() == id)
        {
            return true;
        }
        int[] impostors = client.getObjectDefinition(object.getId()).getImpostorIds();
        if (impostors != null)
        {
            for (int impostor : impostors)
            {
                if (impostor == id)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getObjectConfig(TileObject object)
    {
        if (object instanceof GameObject)
        {
            return ((GameObject) object).getConfig();
        }
        if (object instanceof GroundObject)
        {
            return ((GroundObject) object).getConfig();
        }
        if (object instanceof DecorativeObject)
        {
            return ((DecorativeObject) object).getConfig();
        }
        if (object instanceof WallObject)
        {
            return ((WallObject) object).getConfig();
        }
        return 10;
    }

    private static Renderable getRenderable(TileObject object)
    {
        if (object instanceof GameObject)
        {
            return ((GameObject) object).getRenderable();
        }
        if (object instanceof GroundObject)
        {
            return ((GroundObject) object).getRenderable();
        }
        if (object instanceof DecorativeObject)
        {
            DecorativeObject decorative = (DecorativeObject) object;
            return decorative.getRenderable() != null ? decorative.getRenderable() : decorative.getRenderable2();
        }
        if (object instanceof WallObject)
        {
            WallObject wall = (WallObject) object;
            return wall.getRenderable1() != null ? wall.getRenderable1() : wall.getRenderable2();
        }
        return null;
    }

    private void message(String text)
    {
        // LOGGED_IN fires slightly before the local player exists. Several
        // chat subscribers assume it is already non-null.
        if (client.getLocalPlayer() != null)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[World Builder] " + text, null);
        }
        else
        {
            log.debug("World Builder startup message: {}", text);
        }
    }

    @FunctionalInterface
    private interface PlacementMutation
    {
        void apply(PropPlacement placement);
    }
}
