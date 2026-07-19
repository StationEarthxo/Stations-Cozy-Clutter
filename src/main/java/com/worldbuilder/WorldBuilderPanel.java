package com.worldbuilder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class WorldBuilderPanel extends PluginPanel
{
    private static final int PREVIEW_SIZE = 91;
    private static final int ICON_CACHE_SIZE = 250;
    private static final int RESULT_PAGE_SIZE = 24;

    private final WorldBuilderPlugin plugin;
    private final JTextField search = new JTextField();
    private final JLabel status = new JLabel("Loading object catalogue…");
    private final JPanel results = new JPanel(new GridLayout(0, 2, 5, 5));
    private final JScrollPane resultsScroll = new JScrollPane(results);
    private final JButton previousPage = new JButton("\u25C0");
    private final JButton nextPage = new JButton("\u25B6");
    private final JLabel pageStatus = new JLabel("0 matches", SwingConstants.CENTER);
    private final Timer searchTimer;
    private volatile int resultGeneration;
    private List<CatalogEntry> currentResults = Collections.emptyList();
    private int currentPage;
    private boolean currentResultsComplete;
    private final Map<Integer, ImageIcon> iconCache = new LinkedHashMap<Integer, ImageIcon>(ICON_CACHE_SIZE, .75f, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ImageIcon> eldest)
        {
            return size() > ICON_CACHE_SIZE;
        }
    };

    WorldBuilderPanel(WorldBuilderPlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel title = new JLabel("STATION'S COZY CLUTTER", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.NORTH);
        search.setToolTipText("Search by object name or ID");
        header.add(search, BorderLayout.CENTER);
        status.setForeground(Color.LIGHT_GRAY);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(status, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        results.setBackground(ColorScheme.DARK_GRAY_COLOR);
        resultsScroll.setBorder(null);
        resultsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(24);
        add(resultsScroll, BorderLayout.CENTER);

        JPanel pager = new JPanel(new BorderLayout(5, 0));
        pager.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousPage.setToolTipText("Previous results page");
        nextPage.setToolTipText("Next results page");
        previousPage.addActionListener(event -> changePage(-1));
        nextPage.addActionListener(event -> changePage(1));
        pageStatus.setForeground(Color.LIGHT_GRAY);
        pager.add(previousPage, BorderLayout.WEST);
        pager.add(pageStatus, BorderLayout.CENTER);
        pager.add(nextPage, BorderLayout.EAST);
        add(pager, BorderLayout.SOUTH);

        searchTimer = new Timer(220, event -> plugin.searchCatalogue(search.getText()));
        searchTimer.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent event) { searchTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { searchTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { searchTimer.restart(); }
        });
    }

    void setCatalogueProgress(int loaded, int total)
    {
        SwingUtilities.invokeLater(() -> status.setText(total <= 0 ? "Loading…" : loaded + " / " + total + " objects scanned"));
    }

    void catalogueReady(int count)
    {
        SwingUtilities.invokeLater(() ->
        {
            status.setText(count + " placeable objects — type to search");
            plugin.searchCatalogue(search.getText());
        });
    }

    void catalogueWaitingForCache()
    {
        SwingUtilities.invokeLater(() -> status.setText("Waiting for the game cache..."));
    }

    void showResults(List<CatalogEntry> entries, boolean complete)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentResults = entries;
            currentResultsComplete = complete;
            currentPage = 0;
            renderResultPage();
        });
    }

    private void changePage(int offset)
    {
        int pageCount = Math.max(1, (currentResults.size() + RESULT_PAGE_SIZE - 1) / RESULT_PAGE_SIZE);
        int requestedPage = Math.max(0, Math.min(pageCount - 1, currentPage + offset));
        if (requestedPage == currentPage)
        {
            return;
        }
        currentPage = requestedPage;
        renderResultPage();
    }

    private void renderResultPage()
    {
        final int generation = ++resultGeneration;
        results.removeAll();

        int pageCount = Math.max(1, (currentResults.size() + RESULT_PAGE_SIZE - 1) / RESULT_PAGE_SIZE);
        int start = currentPage * RESULT_PAGE_SIZE;
        int end = Math.min(currentResults.size(), start + RESULT_PAGE_SIZE);
        for (int i = start; i < end; i++)
        {
            CatalogEntry entry = currentResults.get(i);
            JButton button = new JButton("<html><center>" + escape(entry.name) + "<br><small>#" + entry.objectId + "</small></center></html>");
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setPreferredSize(new Dimension(101, 128));
            button.setToolTipText(entry.name + " (object " + entry.objectId + ")");
            button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            button.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
            button.addActionListener(event -> plugin.selectCatalogueEntry(entry));
            ImageIcon cached = iconCache.get(entry.objectId);
            if (cached != null)
            {
                button.setIcon(cached);
            }
            else
            {
                plugin.requestPreview(entry, generation, image ->
                {
                    if (image != null && generation == resultGeneration)
                    {
                        ImageIcon icon = new ImageIcon(image);
                        iconCache.put(entry.objectId, icon);
                        button.setIcon(icon);
                    }
                });
            }
            results.add(button);
        }

        if (currentResults.isEmpty())
        {
            JLabel empty = new JLabel(currentResultsComplete ? "No matching objects" : "Catalogue is still loading…", SwingConstants.CENTER);
            empty.setForeground(Color.LIGHT_GRAY);
            results.add(empty);
        }

        previousPage.setEnabled(currentPage > 0);
        nextPage.setEnabled(currentPage + 1 < pageCount);
        pageStatus.setText(currentResults.isEmpty()
            ? "0 matches"
            : (currentPage + 1) + " / " + pageCount + " - " + currentResults.size() + " matches");
        results.revalidate();
        results.repaint();
        SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(0));
    }

    boolean isPreviewGenerationCurrent(int generation)
    {
        return generation == resultGeneration;
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
