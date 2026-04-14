SET NAMES utf8mb4;

INSERT IGNORE INTO `system_kv` (`k`, `v`) VALUES
('recommend.global.enabled', '1'),
('ai.generation.enabled', '1');

INSERT IGNORE INTO `campus_canteen` (`id`, `campus_name`, `display_name`, `sort_order`) VALUES
('north-1', '主校区', '北苑一食堂 · 药膳档', 1),
('east-2', '主校区', '东苑二食堂 · 轻养窗', 2),
('south-unpub', '南校区', '南苑二食堂（演示：日历未发布）', 3);

INSERT INTO `campus_scene` (`id`, `name`, `icon`, `description`, `tags_json`, `extra_json`, `sort_order`) VALUES
(1, '考前突击·熬夜救星', '/recipe-covers/recipe-1-placeholder.svg', '明天就要交作业或考试周，眼干脑胀还爆痘', '["明目","养肝","滋阴","清热","安神"]', '{"tagline":"为挑灯夜战的你，守护最后一道防线","painTags":["眼干眼涩","疲劳乏力","记忆力下降","爆痘"],"forbidden":["熬夜后忌骤然大补，宜清淡平补","少浓茶咖啡连喝","忌高油夜宵加重胃火痤疮"],"ingredientInsight":"枸杞滋补肝肾、益精明目；菊花散风清热、平肝明目。熬夜久视耗肝血、虚火上扰，宜清肝养阴柔润。","teas":[{"title":"菊花枸杞茶","body":"菊花6g、枸杞10g，沸水冲泡8～10分钟温饮。","type":"tea"},{"title":"间歇护眼","body":"每25分钟远眺20秒并用力眨眼，减轻干眼。","type":"tip"}]}', 1),
(2, '体测来临·能量满格', '/recipe-covers/recipe-2-placeholder.svg', '800/1000米或力量训练后，肌肉酸胀体力见底', '["补气","温阳","补血","强筋骨"]', '{"tagline":"科学补给，让身体告别体测后遗症","painTags":["肌肉酸痛","体力透支","容易抽筋"],"forbidden":["忌赛后立刻冰水猛灌","忌空腹高强度训练"],"ingredientInsight":"黄芪补气升阳；当归补血活血；生姜温中散寒。运动耗气伤津又易寒凝肌肉，宜温补气血、柔和舒展。","teas":[{"title":"生姜红糖水","body":"生姜3片、红糖适量，温水小煮5分钟运动后少量补充。","type":"tea"},{"title":"小腿拉伸","body":"体测前后做腓肠肌拉伸各30秒，减少抽筋。","type":"tip"}]}', 2),
(3, '外卖胃·脾胃告急', '/recipe-covers/recipe-3-placeholder.svg', '重油重盐外卖连轴转，胃胀反酸不消化', '["健脾","和胃","消食","温中"]', '{"tagline":"告别重油重盐，给肠胃放个假","painTags":["胃胀","反酸","不消化","食欲不振"],"forbidden":["忌暴饮暴食连环夜宵","忌冰饮配重辣"],"ingredientInsight":"山药健脾固肾；红枣补中益气；生姜温中止呕。外卖多油腻碍脾，宜补脾和胃、温运中焦。","teas":[{"title":"陈皮山楂水","body":"陈皮5g、山楂6g，热水闷泡10分钟助消化。","type":"tea"},{"title":"饭后散步","body":"进食后慢走10～15分钟，助消化匠气。","type":"tip"}]}', 3),
(4, '换季敏感·免疫防线', '/recipe-covers/recipe-4-placeholder.svg', '气温忽冷忽热，鼻塞咽痛易感冒', '["解表","益气","温中","润肺"]', '{"tagline":"换季不慌张，为身体建起防护墙","painTags":["鼻塞流涕","喉咙干痒","容易感冒"],"forbidden":["忌汗出当风","忌熬夜削弱卫气"],"ingredientInsight":"生姜解表散寒；黄芪固表益气；银耳润肺养阴。风寒与燥邪并存时，宜扶正解表、润而不腻。","teas":[{"title":"姜枣茶","body":"生姜3片、红枣3枚掰开，煮10分钟趁热小口喝。","type":"tea"},{"title":"盐水漱喉","body":"温淡盐水早晚漱喉10秒，缓解咽干痒勿吞。","type":"tip"}]}', 4),
(5, '鸭梨山大·情绪内耗', '/recipe-covers/recipe-5-placeholder.svg', '论文答辩、实习面试，焦虑胸闷睡不着', '["疏肝","解郁","理气","安神","养心"]', '{"tagline":"疏肝解郁，让坏心情烟消云散","painTags":["焦虑","失眠","胸闷","想发脾气"],"forbidden":["忌酒精麻痹情绪","忌通宵刷短视频加重焦虑"],"ingredientInsight":"菊花清肝散热；酸枣仁养心安神；枸杞柔肝。肝气郁结易胸闷烦躁，宜清肝柔肝、宁心安神。","teas":[{"title":"玫瑰佛手茶","body":"干玫瑰花5朵、佛手片3g，闷泡代茶，情志不畅时饮用。","type":"tea"},{"title":"478呼吸","body":"吸气4秒屏息7秒呼气8秒，循环3轮，缓解急性焦虑。","type":"tip"}]}', 5),
(6, '码字赶Due·屏幕眼', '/recipe-covers/recipe-6-placeholder.svg', '赶Due盯屏一整天，眼干肩硬还模糊', '["明目","清热","养肝","滋阴"]', '{"tagline":"你的电子设备眼，需要一场雨露滋润","painTags":["眼睛干涩","视力模糊","肩颈僵硬"],"forbidden":["忌暗环境长时间盯屏","忌用力揉眼"],"ingredientInsight":"决明子清肝明目；菊花散风热；枸杞养肝血。久视伤血，肝开窍于目，宜清热与养阴并用。","teas":[{"title":"菊花决明子茶","body":"菊花5g、决明子6g，沸水冲泡加盖5分钟，白天饮用。","type":"tea"},{"title":"肩颈米字操","body":"缓慢做颈部前后左右与旋转各5次，放松上斜方肌。","type":"tip"}]}', 6),
(7, '上火冒痘·炎症风暴', '/recipe-covers/recipe-7-placeholder.svg', '熬夜烧烤后口疮龈肿，脸上痘痘此起彼伏', '["清热","解毒","养阴"]', '{"tagline":"清热降火，和痘痘说拜拜","painTags":["口腔溃疡","牙龈肿痛","面部痤疮"],"forbidden":["忌辛辣烧烤连环熬夜","脾胃虚寒者不宜寒凉过量"],"ingredientInsight":"绿豆清心胃热毒；菊花平肝胃火；决明子清肝。实火上炎可见口疮龈肿痤疮，宜清热不伤正。","teas":[{"title":"绿豆甘草水","body":"绿豆30g煮开花后取汤，少加甘草2g调和，少量频服。","type":"tea"},{"title":"口腔护理","body":"进食后温水漱口，避免残屑刺激溃疡面。","type":"tip"}]}', 7),
(8, '手脚冰凉·畏寒星人', '/recipe-covers/recipe-8-placeholder.svg', '教室空调冷、四肢不温，生理期更怕冷', '["温阳","补血","散寒","活血","暖宫"]', '{"tagline":"从内而外暖起来，不做冰美人","painTags":["怕冷","手脚不温","生理期不适"],"forbidden":["忌贪凉冰饮配露脐装","经量多者活血方需个体评估"],"ingredientInsight":"当归补血活血；生姜散寒温中；红糖温经散寒。阳虚血弱则四末不温，宜温阳补血并行。","teas":[{"title":"红糖姜枣茶","body":"红糖、生姜3片、红枣3枚，小煮10分钟温热饮用。","type":"tea"},{"title":"温水泡脚","body":"40℃左右水泡脚至小腿10分钟，睡前促循环糖尿患者慎烫。","type":"tip"}]}', 8),
(9, '彻夜难眠·睡个好觉', '/recipe-covers/recipe-9-placeholder.svg', '躺下脑子停不下来，多梦易醒白天更累', '["安神","养心","滋阴"]', '{"tagline":"安神助眠，愿你一夜好梦","painTags":["入睡困难","多梦易醒","醒后疲惫"],"forbidden":["忌睡前2小时剧烈运动","忌睡前咖啡因与刺激刷剧"],"ingredientInsight":"酸枣仁养心益肝安神；百合清心安神；银耳滋阴润燥。阴血不足则阳不入阴，宜养阴安神。","teas":[{"title":"酸枣仁百合茶","body":"炒酸枣仁10g捣碎、百合10g，水煎或闷泡睡前1小时温饮。","type":"tea"},{"title":"固定起床时间","body":"即使失眠也固定起床，重建节律比补觉更有效。","type":"tip"}]}', 9),
(10, '健康减脂·科学掉秤', '/recipe-covers/recipe-10-placeholder.svg', '平台期焦虑，怕饿又怕反弹', '["健脾","清热","养阴","利湿"]', '{"tagline":"吃饱吃好，健康地瘦下来","painTags":["易胖体质","代谢缓慢","减肥平台期"],"forbidden":["忌极端节食导致暴食反弹","忌把代糖饮料当水"],"ingredientInsight":"山药健脾助运；绿豆汤清湿热；银耳高纤维润燥。减重贵在健脾助运与适度清热利湿，忌蛮力克伐伤脾。","teas":[{"title":"山药薏米水","body":"山药片15g、炒薏米12g，煮水代茶，油腻餐后更合适。","type":"tea"},{"title":"蛋白质优先","body":"每餐先吃豆鱼蛋瘦肉再吃主食，稳血糖更易坚持。","type":"tip"}]}', 10)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `icon` = VALUES(`icon`),
  `description` = VALUES(`description`),
  `tags_json` = VALUES(`tags_json`),
  `extra_json` = VALUES(`extra_json`),
  `sort_order` = VALUES(`sort_order`);

INSERT IGNORE INTO `ingredient` (`id`, `name`, `category`, `note`) VALUES
(1, '枸杞', '药食同源', '滋补肝肾'),
(2, '菊花', '药食同源', '清肝明目'),
(3, '黄芪', '中药', '补气固表'),
(4, '当归', '中药', '补血活血'),
(5, '生姜', '调料', '温中散寒');

INSERT IGNORE INTO `recipe` (`id`, `name`, `cover_url`, `efficacy_summary`, `collect_count`, `season_tags`, `constitution_tags`, `efficacy_tags`, `symptom_tags`, `instruction_summary`, `contraindication`, `status`) VALUES
(1, '枸杞菊花茶', '', '清肝明目', 1280, 'spring,summer,autumn', 'yinxu,qiyu,pinghe', '明目,清热,疏肝', '眼干,眼涩,视疲劳,熬夜,爆痘,上火', '枸杞与菊花沸水冲泡 5 分钟即可。', '脾胃虚寒者少饮', 1),
(2, '黄芪炖鸡', '', '补中益气', 2100, 'autumn,winter', 'qixu,yangxu,pinghe', '补气,温中', '体力透支,易感冒,乏力,气虚', '鸡肉焯水后与黄芪文火炖煮 1 小时。', '实热证慎用', 1),
(3, '姜枣茶', '', '驱寒暖胃', 2200, 'winter,spring', 'yangxu,tanshi,qixu', '温中,解表,益气', '鼻塞,流涕,怕冷,胃胀,反酸', '生姜切片与红枣煮 15 分钟。', '阴虚火旺者慎用', 1),
(4, '酸枣仁茶', '', '养心安神', 1500, 'autumn,summer', 'yinxu,qiyu,pinghe', '安神,养心', '失眠,焦虑,多梦,入睡困难', '酸枣仁捣碎后煎煮取汁饮用。', '腹泻者慎用', 1),
(5, '绿豆汤', '', '清热解毒', 3200, 'summer', 'shire,yinxu,pinghe', '清热,解毒', '口腔溃疡,牙龈肿痛,痤疮,上火', '绿豆煮至开花，可加少量冰糖。', '脾胃虚寒不宜多服', 1),
(6, '当归生姜羊肉汤', '', '温阳补血', 2000, 'winter,autumn', 'yangxu,xueyu,qixu', '温阳,补血,散寒', '肌肉酸痛,怕冷,手脚不温,痛经', '羊肉焯水后与当归、生姜同炖。', '湿热体质慎用', 1),
(7, '红糖姜枣茶', '', '暖宫驱寒', 2800, 'winter', 'yangxu,xueyu,qixu', '暖宫,活血,温中', '生理期不适,畏寒,手脚冷', '红糖、姜、红枣煮水饮用。', '糖尿病者酌量', 1),
(8, '菊花决明子茶', '', '清肝明目', 2400, 'spring,summer', 'shire,yinxu,pinghe', '明目,清热,养肝', '视力模糊,眼干,屏幕眼,爆痘', '菊花、决明子冲泡代茶饮。', '低血压、腹泻慎用', 1),
(9, '山药红枣粥', '', '健脾益气', 590, 'spring,autumn', 'qixu,tanshi,pinghe', '健脾,益气', '胃胀,不消化,食欲不振,代谢缓慢', '山药、红枣与大米同煮至粘稠。', '湿盛中满者酌用', 1),
(10, '银耳雪梨汤', '', '润肺生津', 520, 'autumn,winter', 'yinxu,tanshi,pinghe', '润肺,养阴', '喉咙干痒,咽干,熬夜,皮肤干燥', '银耳泡发后与雪梨、冰糖炖煮。', '风寒咳嗽不宜', 1);

DELETE FROM `scene_recipe` WHERE `scene_id` BETWEEN 1 AND 10;
INSERT INTO `scene_recipe` (`scene_id`, `recipe_id`) VALUES
(1, 1), (1, 8), (1, 9), (1, 10),
(2, 2), (2, 6),
(3, 9), (3, 3),
(4, 3), (4, 2), (4, 10),
(5, 4), (5, 1),
(6, 8), (6, 1), (6, 10),
(7, 5), (7, 8), (7, 1),
(8, 6), (8, 7), (8, 3),
(9, 4), (9, 10),
(10, 9), (10, 5), (10, 10);

INSERT IGNORE INTO `recipe_ingredient` (`recipe_id`, `ingredient_id`, `amount_text`) VALUES
(1, 1, '10g'), (1, 2, '5g'),
(2, 3, '15g'), (2, 1, '10g'),
(3, 5, '20g');
