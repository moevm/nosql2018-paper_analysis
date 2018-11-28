const api = "http://localhost:8888/graph/";

$(function() {
    $("#input-file-button").click(function(){
        $("#input-file").click();
    });

    $("[data-back]").click(function(){
        const backElem = $(this).data("back");

        $(".screen").hide();
        $("#" + backElem).fadeIn(300);
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
            /*const data = {
                "title": "Как помыть посуду",
                "journal_name": "Домохозяйка",
                "research_field": "Домохозяйство",
                "year": 1999,
                "link": "https://google.com"
            };*/ // <== TEST

            $(".lds-spinner").css("visibility", "hidden");

            $("#search-results").html('<div class="result" data-title="' + data["title"] + '" data-topic="' + data['journal_name'] + '">' +
                '<h2>' + data["title"] + '</h2>' +
                '<p>' + (data['discr'] || '') + '</p>' +
                '<span><b>Журнал: </b>' + data['journal_name'] + '</span>' +
                '<span><b>Тема: </b>' + data['research_field'] + '</span>' +
                '<span><b>Год: </b>' + data['year'] + '</span>' +
                '<a href="' + data['link'] + '" target="_blank">Читать</a>' +
                '</div>'
            );

            
            $(".result").click(function(){
                $(".screen").hide();
                $("#graph").fadeIn(300);

                renderTopicGraph($(this).data("title"), $(this).data("topic"));

                $.get(api + "research_fields/list", function(list){
                    $("#options select[name='theme'] option:not([disabled])").remove();

                    list.forEach(l => {
                        $("#options select[name='theme']").append('<option value="' + l + '">' + l + '</option>');
                    });
                });
            });
        });
    });
});

}