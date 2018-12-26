const api = "http://172.18.14.33:8888/graph/";

$(function() {
    $("[data-back]").click(function(){
        const backElem = $(this).data("back");

        $(".screen").hide();
        $("#" + backElem).fadeIn(300);
    });

    $("#input-file-button").click(function(){
        $(".screen").hide();
        $("#import").fadeIn(300);
    });

    $("#do-import").click(function(){
        $.ajax({
            url: api + 'import',
            type: 'POST',
            data: JSON.stringify(JSON.parse($('#import-data').val())),
            contentType: "application/json; charset=utf-8",
            dataType: 'json',
            success: function(msg){
                alert("Успех");
                console.log(msg);
            }
        });
    });

    $("#get-refs").click(function(){
        $(".screen").hide();
        $("#refs").fadeIn(300);

        $("#refs-area").empty();
        $(".lds-spinner").css("visibility", "visible");
        
        const selfQuote = $("#refs #self-refs").is(":checked") ? "true" : "false";

        $.get(api + "find_reference_cycles?is_with_self_citation=" + selfQuote, renderRefs);
    });

    $("#refs #self-refs").change(function(){
        $("#refs-area").empty();
        $(".lds-spinner").css("visibility", "visible");
        
        const selfQuote = $(this).is(":checked") ? "true" : "false";

        $.get(api + "find_reference_cycles?is_with_self_citation=" + selfQuote, renderRefs);
    });

    $("#export").click(function(){
        window.open(api + "search?is_with_self_citation=true", '_blank');
    });

    $("#search-button").click(function(){
        $(".screen").hide();
        $("#search").fadeIn(300);
    });

    $("#do-search").click(function(){
        const query = $("#search-area input[name='query']").val();

        if( !query.length ) return;

        $(".lds-spinner").css("visibility", "visible");

        $.get(api + "papers/get?title=" + encodeURIComponent(query), function(data){
            $(".lds-spinner").css("visibility", "hidden");

            $("#search-results").html('<div class="result" data-title="' + data["title"] + '" data-topic="' + data['research_field'] + '" data-year="' + data["year"] + '">' +
                '<h2>' + data["title"] + '</h2>' +
                '<span><b>Журнал: </b>' + data['journal_name'] + '</span>' +
                '<span><b>Тема: </b>' + data['research_field'] + '</span>' +
                '<span><b>Год: </b>' + data['year'] + '</span>' +
                '<a href="' + data['link'] + '" target="_blank">Читать</a>' +
                '</div>'
            );

            
            $(".result").click(function(){
                $(".screen").hide();
                $("#graph").fadeIn(300);

                const title = $(this).data("title"),
                    topic = $(this).data("topic"),
                    year = $(this).data("year");

                $.get(api + "search?paper_title=" + title, function(list){
                    const depPapers = [];

                    list.forEach(paper => {
                        paper["references"].forEach(ref => {
                            const currPaper = ref["target_paper_title"];

                            if(depPapers.indexOf(currPaper) === -1){
                                depPapers.push(currPaper);
                            }
                        });
                    });

                    renderTopicGraph(title, topic, year, depPapers);
                });

                $.get(api + "research_fields/list", function(list){
                    $("#options select[name='theme'] option:not([disabled])").remove();

                    list.forEach(l => {
                        $("#options select[name='theme']").append('<option value="' + l + '">' + l + '</option>');
                    });
                });
            });
        });
    });

    $("#get-loops").click(function(){
        $(".screen").hide();
        $("#loops").fadeIn(300);

        $("#loops-area").empty();
        $(".lds-spinner").css("visibility", "visible");

        const selfQuote = $("#loops #self-loops").is(":checked") ? "true" : "false";

        $.get(api + "search?is_with_self_citation=" + selfQuote, renderMainGraph);
    });

    $("#loops #self-loops").change(function(){
        $("#loops-area").empty();
        $(".lds-spinner").css("visibility", "visible");

        const selfQuote = $(this).is(":checked") ? "true" : "false";

        $.get(api + "search?is_with_self_citation=" + selfQuote, renderMainGraph)
    });
});

function renderRefs(data) {
    $(".lds-spinner").css("visibility", "hidden");

    data.papers.forEach((ref, i) => {
        $("#refs-area").append('<div class="ref" data-num="'+i+'"></div>');

        ref.forEach((paper, j, arr) => {
            $(".ref[data-num='" + i + "']").append("<span class='paper'><b>" + paper.author_name + "</b>: " + paper.paper_title + (j + 1 < arr.length ? " => " : "") + "</span>");
        });
    });
}

function renderTopicGraph(paper, topic, year, list) {
    
    $("#graph-area").empty();

    const nodesData = [
        //Статя
        { id: 1, label: paper, shape: 'star', color: 'rgb(59, 75, 252)', size: 20, type: 'paper' },
        
        //Тема
        { id: 2, label: topic, shape: 'dot', color: 'rgb(205, 162, 190)', size: 50, type: 'topic' },

        //Год
        { id: 3, label: year + '', shape: 'dot', color: 'yellow', size: 10, type: 'year' },
    ];

    const edgesData = [
        { from: 2, to: 1, color: 'red', arrows:'to' },
        { from: 3, to: 1, color: 'rgb(55, 255, 65)', arrows:'to' }
    ];

    list.forEach((paper, i) => {
        const id = nodesData.length + i + 1;

        nodesData.push({ id: id, label: paper, shape: 'dot', color: 'rgb(59, 75, 252)', size: 20, type: 'paper' });
        edgesData.push({ from: id, to: 1, color: 'blue', arrows:'to' });
    });

    const nodes = new vis.DataSet(nodesData);
    const edges = new vis.DataSet(edgesData);

    const container = document.getElementById('graph-area');
    const data = {
        nodes: nodes,
        edges: edges
    };
    const options = {
        physics: false,
        interaction: {
            dragNodes: true,// do not allow dragging nodes
            zoomView: false, // do not allow zooming
            dragView: false  // do not allow dragging
        }
    };
    const network = new vis.Network(container, data, options);

    network.on('click', function(properties) {
        const ids = properties.nodes;
        const clickedNodes = nodes.get(ids);

        if (clickedNodes.length) {
            const currPapers = nodesData.filter(n => {
                return clickedNodes.find(s => (s.id == n.id && n.type === 'paper')) !== undefined;
            });
			
			const currTopics = nodesData.filter(n => {
                return clickedNodes.find(s => (s.id == n.id && n.type === 'topic')) !== undefined;
            });
			

            if(currPapers.length) {
                $.get(api + "search?paper_title=" + currPapers[0].label, renderPapersGraph);
            } else if (currTopics.length) {
				$.get(api + "search?research_field=" + currTopics[0].label, renderPapersGraph);
			}
        }
    });
}

function renderPapersGraph(list) {

    $("#loops").empty();

    const nodesData = [];
    const edgesData = [];

    list.forEach(l => {
		l.references.forEach(d => {
			const from = d['source_paper_title'];
			const to = d['target_paper_title'];

			let fromIndex = nodesData.findIndex(n => n.label === from);

			if (fromIndex == -1){
				nodesData.push({ id: nodesData.length, label: from, shape: 'dot', size: 30, color: '#97C2FC' });
				fromIndex = nodesData.length - 1;
			}

			let toIndex = nodesData.findIndex(n => n.label === to);

			if (toIndex == -1){
				nodesData.push({ id: nodesData.length, label: to, shape: 'dot', size: 30, color: '#97C2FC' });
				toIndex = nodesData.length - 1;
			}

			if (edgesData.findIndex(e => e.from == fromIndex && e.to == toIndex) == -1 ){
				edgesData.push({ from: fromIndex, to: toIndex, color:{color:'red'}, arrows:'to' });
			}
		});
	});

    const nodes = new vis.DataSet(nodesData);
    const edges = new vis.DataSet(edgesData);

    const container = document.getElementById('graph-area');
    const data = {
        nodes: nodes,
        edges: edges
    };
    const options = {
        physics: false,
        interaction: {
            dragNodes: true,// do not allow dragging nodes
            zoomView: false, // do not allow zooming
            dragView: false  // do not allow dragging
        }
    };
    const network = new vis.Network(container, data, options);

}

function renderMainGraph(list) {
    $(".lds-spinner").css("visibility", "hidden");
    $("#loops-area").show();

    const nodesData = [];
    const edgesData = [];

    let num = 1;
    list.forEach(paper => {
        const title = paper["title"],
            journal = paper["journal_name"],
            year = '' + paper["year"],
            topic = paper["research_field"];
			
		const authors = (paper["authors"] || []).map(a => a.name).join(", ");

        let index = nodesData.findIndex(n => n.label === title && n.type === 'paper');
        if(index === -1) {
            index = num;

            nodesData.push({ id: index, label: title, title: '<b>Авторы:</b> ' + authors, shape: 'dot', color: 'rgb(59, 75, 252)', size: 20, type: 'paper' });
        }

        const jIndex = nodesData.findIndex(n => n.label === journal && n.type === 'journal');
        if(jIndex === -1) {
            num++;

            nodesData.push({ id: num, label: journal, shape: 'dot', color: 'yellow', size: 20, type: 'journal' });
            edgesData.push({ from: index, to: num, color: {color: 'green'}, arrows:'to' });
        } else {
            edgesData.push({ from: index, to: nodesData[jIndex].id, color: {color: 'green'}, arrows:'to' });
        }

        const yIndex = nodesData.findIndex(n => n.label === year && n.type === 'year');
        if(yIndex === -1) {
            num++;

            nodesData.push({ id: num, label: year, shape: 'dot', color: '#8d00ff', size: 20, type: 'year' });
            edgesData.push({ from: index, to: num, color: {color: 'red'}, arrows:'to' });
        } else {
            edgesData.push({ from: index, to: nodesData[yIndex].id, color: {color: 'red'}, arrows:'to' });
        }

        const tIndex = nodesData.findIndex(n => n.label === topic && n.type === 'topic');
        if(tIndex === -1) {
            num++;

            nodesData.push({ id: num, label: topic, shape: 'dot', color: 'orange', size: 20, type: 'topic' });
            edgesData.push({ from: index, to: num, color: {color: 'orange'}, arrows:'to' });
        } else {
            edgesData.push({ from: index, to: nodesData[tIndex].id, color: {color: 'orange'}, arrows:'to' });
        }

        num++;
    });

    list.forEach((paper) => {
        const from = nodesData.find(n => n.label === paper.title);

        if(from) {
            paper.references.forEach(ref => {
                const to = nodesData.find(n => n.label === ref["target_paper_title"]);

                if(to){
                    edgesData.push({ from: from.id, to: to.id, color: {color: 'black'}, arrows:'to' });
                } else {
                    const index = nodesData.length + 1;

                    nodesData.push({ id: index, label: ref["target_paper_title"], shape: 'dot', color: 'rgb(59, 75, 252)', size: 20 });
                    edgesData.push({ from: from.id, to: index, color: {color: 'black'}, arrows:'to' });
                }
            });
        }
    });

    const nodes = new vis.DataSet(nodesData);
    const edges = new vis.DataSet(edgesData);

    const container = document.getElementById('loops-area');
    const data = {
        nodes: nodes,
        edges: edges
    };
    const options = {
        physics: false,
        interaction: {
            dragNodes: true,// do not allow dragging nodes
            zoomView: true, // do not allow zooming
            dragView: true  // do not allow dragging
        }
    };
    const network = new vis.Network(container, data, options);

    network.on('click', function(properties) {
        const ids = properties.nodes;
        const clickedNodes = nodes.get(ids);

        if (clickedNodes.length) {
            const currPapers = nodesData.filter(n => {
                return clickedNodes.find(s => (s.id == n.id && n.type === 'paper')) !== undefined;
            });
			
			const currTopics = nodesData.filter(n => {
                return clickedNodes.find(s => (s.id == n.id && n.type === 'topic')) !== undefined;
            });
			

            if(currPapers.length) {
                $.get(api + "search?paper_title=" + currPapers[0].label + "&is_with_self_citation=" + ($("#loops #self-loops").is(":checked") ? "true" : "false"), renderMainGraph);
            } else if (currTopics.length) {
				$.get(api + "search?research_field=" + currTopics[0].label + "&is_with_self_citation=" + ($("#loops #self-loops").is(":checked") ? "true" : "false"), renderMainGraph);
			}
        }
    });
}