package forge.achievement;

import forge.properties.ForgeConstants;

public class DraftAchievements extends AchievementCollection {
    public DraftAchievements() {
        super("Booster Draft", ForgeConstants.ACHIEVEMENTS_DIR + "draft.xml");
    }

    //add achievements that should appear at the top above core achievements for each game mode
    @Override
    protected void buildTopShelf() {
    }

    //add achievements that should appear at the bottom below core achievements for each game mode
    @Override
    protected void buildBottomShelf() {
    }
}
